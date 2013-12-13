/*
 * Copyright (C) 20/11/13 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.geocite.marius.one

import scala.util.Random
import fr.geocite.simpuzzle._
import distribution._
import fr.geocite.marius.one.matching.Matching
import fr.geocite.marius._
import fr.geocite.gis.distance.GeodeticDistance
import scalaz._

trait Marius <: StepByStep
    with TimeEndingCondition
    with MariusLogging
    with Matching
    with MariusState
    with MariusFile
    with WealthFromPopulation
    with PositionDistribution
    with GeodeticDistance {

  def adjustConsumption: Double

  def adjustProductivity: Double

  def territorialTaxes: Double

  def capitalShareOfTaxes: Double

  def wealthSavingRate: Double = 0.0

  def fixedCost: Double = 0.0

  def internalShare: Double = 0.0

  def wealth: Lens[CITY, Double]
  def region: Lens[CITY, String]
  def capital: Lens[CITY, Boolean]
  def saving: Lens[CITY, Double]
  def distanceMatrix: Lens[STATE, DistanceMatrix]

  def nextState(s: STATE)(implicit rng: Random) = {

    //def aboveOne(v: Double) = if (v <= 1) 1.0 else v
    val tBalance = territoryBalance(cities.get(s))

    for {
      wealths <- wealths(s, tBalance)
    } yield {
      def populations = wealths.map { wealthToPopulation }

      def savings =
        cities.get(s).map(c => wealth.get(c) * wealthSavingRate)

      val newCities =
        (cities.get(s) zip populations zip wealths zip savings).map(flatten).map {
          case (c, p, w, s) =>
            assert(p >= 0, s"The population is negative $p, $w")
            assert(w >= 0, s"The city too poor for the model $w, $p")
            saving.set(wealth.set(population.set(c, p), w), s)
        }

      cities.set(step.mod(_ + 1, s), newCities)
    }
  }

  def wealthToPopulation(wealth: Double): Double

  def wealths(s: STATE, tbs: Seq[Double])(implicit rng: Random) = {
    val supplies = cities.get(s).map(c => supply(population.get(c), population.get(c)))
    val demands = cities.get(s).map(c => demand(population.get(c)))

    val Matched(transactions, unsolds, unsatisfieds) = matchCities(s, supplies, demands)

    val transactedFrom: Map[Int, Seq[Transaction]] =
      transactions.groupBy(_.from).withDefaultValue(Seq.empty)

    val transactedTo: Map[Int, Seq[Transaction]] =
      transactions.groupBy(_.to).withDefaultValue(Seq.empty)

    def bonuses = {
      val allTransactionsDist =
        for {
          cid <- 0 until cities.get(s).size
          tfrom = transactedFrom(cid)
          tto = transactedTo(cid)
        } yield tfrom ++ tto

      def nbDist = allTransactionsDist.map(_.size.toDouble).centered.reduced
      def nbTotal = allTransactionsDist.map(_.map(_.transacted).sum).centered.reduced
      (nbDist zip nbTotal).map { case (x, y) => x + y }
    }

    log(
      (cities.get(s) zip supplies zip demands zip unsolds zip unsatisfieds zip bonuses zip tbs).map(flatten).map {
        case (city, supply, demand, unsold, unsatified, bonus, tb) =>
          wealth.get(city) +
            supply -
            internalShare * demand * 2 +
            demand -
            fixedCost +
            bonus -
            unsold +
            unsatified +
            tb
      }.map {
        w => if (w >= 0) w else 0
      },
      transactions)
  }

  def consumption(population: Double) = adjustConsumption * math.log(population + 1)

  def productivity(wealth: Double) = adjustProductivity * math.log(wealth + 1)

  def demand(population: Double) = consumption(population) * population

  def supply(population: Double, wealth: Double) = productivity(wealth) * population

  def territoryBalance(s: Seq[CITY]): Seq[Double] = {
    val deltas =
      for {
        (r, cs) <- s.zipWithIndex.groupBy(c => region.get(c._1))
        (cities, indexes) = cs.unzip
      } yield {
        val taxes = cities.map(c => wealth.get(c) * territorialTaxes)
        val capitalShare = capitalShareOfTaxes * taxes.sum
        val taxesLeft = taxes.sum - capitalShare
        val regionPopulation = cities.map(c => population.get(c)).sum

        val territorialDeltas = (cities zip taxes).map {
          case (city, cityTaxes) =>
            val populationShare = population.get(city) / regionPopulation

            val delta =
              (if (capital.get(city)) taxesLeft * populationShare + capitalShare
              else taxesLeft * populationShare) - cityTaxes
            delta
        }
        indexes zip territorialDeltas
      }

    deltas.flatten.toSeq.sortBy {
      case (i, _) => i
    }.unzip._2
  }

  def distances(implicit rng: Random) = {
    val positions = positionDistribution(rng).toIndexedSeq

    positions.zipWithIndex.map {
      case (c1, i) =>
        positions.zipWithIndex.map { case (c2, _) => distance(c1, c2) }
    }
  }

}
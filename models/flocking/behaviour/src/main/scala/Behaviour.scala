/*
 * Copyright (C) 20/05/2014 Guillaume Chérel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import fr.iscpif.flocking.model.engine._
import fr.iscpif.flocking.model.interactions._
import fr.iscpif.flocking.model.birds._
import fr.iscpif.flocking.model.datatypes._
import scala.util.Random
import scala.math._


trait Behaviour {
    // type NGroups = Int
    // type AvgVelocity = Double
    // type RelativeDiffusion = Double
    // type B = ([NGroups], [AvgVelocity], [RelativeDiffusion])

    val model: Model

    def countGroups(gb: GraphBirds): Int = countGroups(gb, 0, (0 until gb.birds.size).toSet)
    def countGroups(gb: GraphBirds, nclustersFound: Int, remaining: Set[Int]): Int = {
      if (remaining.size == 0) nclustersFound
      else countGroups(gb, nclustersFound + 1, remaining -- extractComponent(gb, remaining.head, Set()))
    }

    def extractComponent(gb: GraphBirds, start: Int, visited: Set[Int]): Set[Int] = {
      if (gb.birds.size == 0) Set()
      else {
        val neighbours: Seq[Int] = gb.flockmates(start)
        if (neighbours.size == 0) Set(start)
        else neighbours.foldLeft(visited + start)((a:Set[Int], b:Int) => if (!a.contains(b)) extractComponent(gb, b, a) else a)
      }
    }

    def nearestNeighbour(d: DistMatrix)(i: Int, birds: Seq[Bird]): Int = {
      birds.indices.minBy(j => if (i != j) d(i, j) else Double.MaxValue)
    }

    def voronoiNeighbours(birds: Seq[Bird], dm: DistMatrix): Seq[Seq[Int]] = {
      val nnf = nearestNeighbour(dm)_
      val nn = for {i <- birds.indices} yield nnf(i, birds)
      for {i <- birds.indices} yield voronoiNeighbours(birds, nn, i)
    }
    def voronoiNeighbours(birds: Seq[Bird], nearestNeigh: Seq[Int], i: Int): Seq[Int] =
      for {j <- birds.indices if ((i != j) && nearestNeigh(j) == i)} yield j

    def kNearestNeighbours(k: Int, birds:Seq[Bird], dm: DistMatrix): Seq[Seq[Int]] = {
      def insert(x: Int, k: Int, nn: List[Int], distFromI: Int => Double): List[Int] =
        if (k == 0) List()
        else if (nn.size == 0) List(x)
        else if (distFromI(x) < distFromI(nn.head)) (x :: nn) take k
        else nn.head :: insert(x, k - 1, nn.tail, distFromI)
      
      def knn(i: Int): Seq[Int] = 
        birds.indices.foldRight(List[Int]())((j,nn) => if (j == i) nn else insert(j, k, nn, {dm(i,_)}))
      
      birds.indices.map(knn(_))
    }

    def distBetween(neighbours: Seq[Seq[Int]], dm: DistMatrix): Seq[Seq[Double]] =
      neighbours.indices.map((i: Int) => neighbours(i).map((j: Int) => dm(i,j)))

    def sumOver(is: Range, f: Int => Double): Double = (is map f).sum
    def averageOver(is: Range, f: Int => Double): Double =
      sumOver(is, f) / (is.size: Double)

    def relativeDiffusion(neighboursDistAtT1: Seq[Seq[Double]],
      neighboursDistAtT2: Seq[Seq[Double]]): Double = {
      averageOver(neighboursDistAtT1.indices, {i => {
        val ni: Double = neighboursDistAtT1(i).size
        (1 / ni) * sumOver(neighboursDistAtT1(i).indices, {j =>
          1 - (pow(neighboursDistAtT1(i)(j), 2) / pow(neighboursDistAtT2(i)(j), 2))
          })
      }
      })
    }

    abstract class AbstractCollector[S, +T]
    case class Collector[S, +T](when: Int, f: S => AbstractCollector[S,T]) extends AbstractCollector[S, T] {
      def collect(modelstate: S): AbstractCollector[S, T] = f(modelstate)
    }
    case class Val[S,+T](f: T) extends AbstractCollector[S, T]

    def collectCountGroups(state: GraphBirds): Double =
      countGroups(state) / (model.populationSize: Double)
    val countGroupsCollector: Collector[GraphBirds, Double] =
      Collector(300, { (s: GraphBirds) => Val(collectCountGroups(s)) })

    def collectRelativeDiffusion(state1: GraphBirds)(state2: GraphBirds): Double = {
      val dm = DistMatrix(state1.birds.map(_.position), model.distanceBetween)
      val neighbs = kNearestNeighbours(3,state1.birds, dm)
      val dist1 = distBetween(neighbs, dm)
      relativeDiffusion(dist1, distBetween(neighbs, DistMatrix(state2.birds.map(_.position), model.distanceBetween)))
    }
    val relativeDiffusionCollector: Collector[GraphBirds, Double] =
      Collector(200, { (s1:GraphBirds) =>
        Collector(300, { (s2: GraphBirds) => Val(collectRelativeDiffusion(s1)(s2))})
        })

    def collectVelocity(state1: GraphBirds)(state2: GraphBirds): Double =
      (state1.birds zip state2.birds).map(x => model.distanceBetween(x._1.position, x._2.position)).sum / (state1.birds.size: Double)
    val velocityCollector: Collector[GraphBirds, Double] =
      Collector(298, { (s1:GraphBirds) =>
        Collector(300, { (s2:GraphBirds) => Val(collectVelocity(s1)(s2))})
        })

    def constructDescription(collectors: Seq[AbstractCollector[GraphBirds, Double]], gb: GraphBirds, iter: Int): Seq[Double] =
      if (collectors.exists(x => x match {case Collector(_,_) => true
        case Val(_) => false })) {
        val updatedCollectors: Seq[AbstractCollector[GraphBirds, Double]] = collectors.map(x => x match {case Collector(i,f) => if (i == iter) f(gb) else x
         case Val(_) => x})
        val updatedState = model.oneStep(gb)
        constructDescription(updatedCollectors, updatedState, iter + 1)
      }
      else collectors.map(_ match { case Val(x) => x } )

    def defaultDescription(implicit rng: Random) = constructDescription(Vector(countGroupsCollector, relativeDiffusionCollector, velocityCollector), model.randomInit, 0)

    trait DistMatrix {
      val distances: Vector[Vector[Double]]
      def apply(i: Int,j: Int): Double =
      if (i == j) 0
      else if (i < j) distances(i)(j - i - 1)
      else apply(j,i)
    }
    object DistMatrix {
      def apply(points: Seq[Point], distFunc: (Point, Point) => Double): DistMatrix = new DistMatrix {
        val distances: Vector[Vector[Double]] = (for {i <- 0 until (points.size - 1)} yield (for {j <- i+1 until points.size} yield distFunc(points(i), points(j))).toVector).toVector
      }
      def euclidean(p1: Point, p2: Point): Double = sqrt(pow(p1.x - p2.x, 2) + pow(p1.y - p2.y,2))
    }

}

object Behaviour{
  def apply(_populationSize : Int,
    _vision: Double,
    _minimumSeparation: Double,
    _maxAlignTurn: Double,
    _maxCohereTurn: Double,
    _maxSeparateTurn: Double
    )(implicit rng: Random) = {
    new Behaviour {
      val model = new Model {
        val worldWidth: Double = 1
        val worldHeight: Double = 1
        val populationSize: Int = _populationSize
        val vision: Double = _vision
        val minimumSeparation: Double = _minimumSeparation
        val maxAlignTurn: Angle = Angle(_maxAlignTurn)
        val maxCohereTurn: Angle = Angle(_maxCohereTurn)
        val maxSeparateTurn: Angle = Angle(_maxSeparateTurn)
        val stepSize: Double = 0.02
        val envDivsHorizontal: Int = 1
        val envDivsVertical: Int = 1
        val visionObstacle: Double = 1
      }
      }.defaultDescription.toArray
  }
}
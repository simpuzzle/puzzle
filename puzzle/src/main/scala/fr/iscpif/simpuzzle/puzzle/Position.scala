/*
 * Copyright (C) 2015 Romain Reuillon
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
package fr.iscpif.simpuzzle.puzzle

object Position {

  implicit val tupleIsPosition = new Position[(Double, Double)] {
    def x(t: (Double, Double)) = t._1
    def y(t: (Double, Double)) = t._2
  }

}

trait Position[T] {
  def x(t: T): Double
  def y(t: T): Double
}

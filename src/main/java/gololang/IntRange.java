/*
 * Copyright 2012-2014 Institut National des Sciences Appliquées de Lyon (INSA-Lyon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gololang;

import java.util.Iterator;
import java.util.NoSuchElementException;

class IntRange implements Iterable<Integer> {
  private final int from;
  private final int to;
  private int increment = 1;
  private int cmp = 1;

  public IntRange(int from, int to) {
    this.from = from;
    this.to = to;
  }

  public int from() {
    return this.from;
  }

  public int to() {
    return this.to;
  }

  public int increment() {
    return this.increment;
  }

  public IntRange incrementBy(int value) {
    this.increment = value;
    if (value < 0) {
      this.cmp = -1;
    }
    return this;
  }

  public IntRange decrementBy(int value) {
    return this.incrementBy(-value);
  }

  @Override
  public String toString() {
    if (this.increment != 1) {
      return String.format("range(%s,%s):incrementBy(%s)", this.from, this.to, this.increment);
    }
    return String.format("range(%s,%s)", this.from, this.to);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new Iterator<Integer>() {

      private boolean started = false;
      private int current = from;

      @Override
      public boolean hasNext() {
        return Integer.compare(to, current) == cmp;
      }

      @Override
      public Integer next() {
        int value = current;
        if (started) {
          if (hasNext()) {
            current = current + increment;
            return value;
          } else {
            throw new NoSuchElementException("iteration has finished");
          }
        } else {
          started = true;
          current = current + increment;
          return value;
        }
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove() is not supported on a range");
      }
    };
  }
}

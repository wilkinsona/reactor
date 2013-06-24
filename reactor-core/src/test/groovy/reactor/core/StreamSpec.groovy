/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package reactor.core

import static reactor.GroovyTestUtils.*

import java.util.concurrent.TimeUnit

import reactor.fn.Function
import reactor.fn.Observable;
import reactor.fn.support.Reduction
import spock.lang.Specification

class StreamSpec extends Specification {

	def 'A deferred Stream with an initial value makes that value available immediately'() {
		given: 'a composable with an initial value'
		StandardStream composable = Streams.defer('test').sync().get()

		when: 'the value is retrieved'
		def value = composable.get()

		then: 'it is available'
		value == 'test'
	}

	def 'A deferred Stream with an initial value passes the value to a consumer'() {
		given: 'a composable with an initial value'
		def values = []
		StandardStream composable = Streams.defer(1).sync().get().consume(consumer { values << it })

		when: 'a value is accepted'
		composable.accept(2)

		then: 'the consumer has been passed the init value'
		values == [1]
	}

	def 'A deferred Stream with an initial value with Future read only supplier'() {
		given: 'a composable with an initial value'
		StandardStream composable = Streams.defer(1).sync().get()

		when: 'a value is accepted'
		def f = composable.future()
		composable.get()

		then: 'the future has been passed the values'
		f.get() == 1
	}

	def 'A Stream with a known set of values makes those values available immediately'() {
		given: 'a composable with values 1 to 5 inclusive'
		StandardStream composable = Streams.each([1, 2, 3, 4, 5]).sync().get()

		when: 'the first value is retrieved'
		def value = composable.first().get()

		then: 'it is 1'
		value == 1

		when: 'the last value is retrieved'
		value = composable.last().get()

		then: 'it is 5'
		value == 5

		when: 'the value is retrieved'
		value = composable.get()

		then: 'it is the last value'
		value == 5
	}

	def "A Stream's initial values are not passed to consumers but subsequent values are"() {
		given: 'a composable with values 1 to 5 inclusive'
		StandardStream composable = Streams.each([1, 2, 3, 4, 5]).sync().get()

		when: 'a Consumer is registered'
		def values = []
		composable.consume(consumer { values << it })

		then: 'it is not called with the initial values'
		values == []

		when: 'get is called'
		composable.get()

		then: 'the initial values are passed'
		values == [1, 2, 3, 4, 5]

		when: 'a subsequent value is accepted'
		composable.accept(6)

		then: 'it is not passed to the consumer'
		values == [1, 2, 3, 4, 5]


	}

	def 'Accepted values are passed to a registered Consumer'() {
		given: 'a composable with a registered consumer'
		StandardStream composable = Streams.defer().sync().get()
		def value
		composable.consume(consumer { value = it })

		when: 'a value is accepted'
		composable.accept(1)

		then: 'it is passed to the consumer'
		value == 1

		when: 'another value is accepted'
		composable.accept(2)

		then: 'it too is passed to the consumer'
		value == 2
	}

	def 'Accepted errors are passed to a registered Consumer'() {
		given: 'a composable with a registered consumer of RuntimeExceptions'
		StandardStream composable = Streams.defer().sync().get()
		def errors = 0
		composable.when(RuntimeException, consumer { errors++ })

		when: 'A RuntimeException is accepted'
		composable.accept(new RuntimeException())

		then: 'it is passed to the consumer'
		errors == 1

		when: 'A checked exception is accepted'
		composable.accept(new Exception())

		then: 'it is not passed to the consumer'
		errors == 1

		when: 'A subclass of RuntimeException is accepted'
		composable.accept(new IllegalArgumentException())

		then: 'it is passed to the consumer'
		errors == 2
	}

	def 'Await will time out if a value is not available'() {
		when: 'a deferred composable'
		StandardStream composable = Streams.defer().sync().get()

		def start = System.currentTimeMillis()
		def value = composable.await(500, TimeUnit.MILLISECONDS)
		def duration = System.currentTimeMillis() - start

		then: 'the await will timeout'
		duration >= 500
		value == null
	}

	def 'A Stream can consume values from another Stream'() {
		given: 'a deferred composable with a consuming Stream'
		StandardStream<Integer> parent = Streams.<Integer>defer().sync().get()
		StandardStream<Integer> child = Streams.<Integer>defer().sync().get()
		parent.consume((StandardComposable<Integer>)child)

		when: 'the parent accepts a value'
		parent.accept(1)

		then: 'it is passed to the child'
		1 == child.get()

		when: "the parent accepts an error and the child's value is accessed"
		parent.accept(new Exception())
		child.get()

		then: 'the child contains the error from the parent'
		thrown(IllegalStateException)
	}

	def 'When the expected accept count is exceeded, last is updated with each new value'() {
		given: 'a composable with a known number of values'
		StandardStream composable = Streams.each([1, 2, 3, 4, 5]).sync().get()

		when: 'last is retrieved'
		StandardStream last = composable.last()

		then: 'its value is the last of the initial values'
		last.get() == 5

		when: 'another value is accepted'
		composable.accept(6)

		then: 'the value of last is not updated'
		last.get() == 5
	}

	def 'When the number of values is unknown, last is never updated'() {
		given: 'a composable that will accept an unknown number of values'
		StandardStream composable = Streams.defer().sync().get()

		when: 'last is retrieved'
		StandardStream last = composable.last()

		then: 'its value is unknown'
		last.get() == null

		when: 'a value is accepted'
		composable.accept(1)

		then: "last's value is still unknown"
		last.get() == null
	}

	def 'When the expectedAcceptCount is reduced to a count that has been reached, last is updated with the latest value'() {
		given: 'a composable that will accept an unknown number of values'
		StandardStream composable = Streams.defer().get()

		when: 'last is retrieved'
		StandardStream last = composable.last()

		then: 'its value is unknown'
		last.get() == null

		when: 'the expected accept count is set and that number of values is accepted'
		composable.accept(1)
		composable.accept(2)
		composable.accept(3)
		composable.setExpectedAcceptCount(2)

		then: "last's value is now that of the last value"
		last.get() == 3
	}

	def "A Stream's values can be mapped"() {
		given: 'a source composable with a mapping function'
		StandardStream source = Streams.defer().get()
		StandardStream mapped = source.map(function { it * 2 })

		when: 'the source accepts a value'
		source.accept(1)

		then: 'the value is mapped'
		mapped.get() == 2
	}

	def "A Stream's values can be filtered"() {
		given: 'a source composable with a filter that rejects odd values'
		StandardStream source = Streams.defer().get()
		StandardStream filtered = source.filter(function { it % 2 == 0 })

		when: 'the source accepts an even value'
		source.accept(2)

		then: 'it passes through'
		filtered.get() == 2

		when: 'the source accepts an odd value'
		source.accept(3)

		then: 'it is blocked by the filter'
		filtered.get() == 2
	}

	def "When a mapping function throws an exception, the mapped composable accepts the error"() {
		given: 'a source composable with a mapping function that throws an exception'
		StandardStream source = Streams.defer().get()
		StandardStream mapped = source.map(function { throw new RuntimeException() })
		def errors = 0
		mapped.when(Exception, consumer { errors++ })

		when: 'the source accepts a value'
		source.accept(1)

		then: 'the error is passed on'
		errors == 1
	}

	def "When a filter function throws an exception, the filtered composable accepts the error"() {
		given: 'a source composable with a filter function that throws an exception'
		StandardStream source = Streams.defer().get()
		StandardStream filtered = source.filter(function { throw new RuntimeException() })
		def errors = 0
		filtered.when(Exception, consumer { errors++ })

		when: 'the source accepts a value'
		source.accept(1)

		then: 'the error is passed on'
		errors == 1
	}

	def "A known set of values can be reduced"() {
		given: 'a composable with a known set of values'
		StandardStream source = Streams.each([1, 2, 3, 4, 5]).sync().get()

		when: 'a reduce function is registered'
		StandardStream reduced = source.reduce(new Reduction())

		then: 'the resulting composable holds the reduced value'
		reduced.get() == 120
	}

	def "When reducing a known set of values, only the final value is passed to consumers"() {
		given: 'a composable with a known set of values and a reduce function'
		StandardStream reduced = Streams.each([1, 2, 3, 4, 5]).sync().get().reduce(new Reduction())

		when: 'a consumer is registered'
		def values = []
		reduced.consume(consumer { values << it })

		// TODO This will pass if get is called, but it shouldn't be necessary. The following test passes without calling
		// get(). The behaviour needs to be consistent irrespective of whether the known number of values is provided up
		// front or via accept.

		reduced.get()

		then: 'the consumer only receives the final value'
		values == [120]
	}

	def "When reducing a known number of values, only the final value is passed to consumers"() {
		given: 'a composable with a known number of values and a reduce function'
		StandardStream source = Streams.defer().sync().get()
		source.setExpectedAcceptCount(5)
		StandardStream reduced = source.reduce(new Reduction())
		def values = []
		reduced.consume(consumer { values << it })

		when: 'the expected number of values is accepted'
		source.accept(1)
		source.accept(2)
		source.accept(3)
		source.accept(4)
		source.accept(5)

		then: 'the consumer only receives the final value'
		values == [120]
	}

	def 'A known number of values can be reduced'() {
		given: 'a composable that will accept 5 values and a reduce function'
		StandardStream source = Streams.defer().sync().get()
		source.expectedAcceptCount = 5

		StandardStream reduced = source.reduce(new Reduction())

		when: 'the expected number of values is accepted'
		source.accept(1)
		source.accept(2)
		source.accept(3)
		source.accept(4)
		source.accept(5)

		then: 'the reduced composable holds the reduced value'
		reduced.get() == 120
	}

	def 'When a known number of values is being reduced, only the final value is made available'() {
		given: 'a composable that will accept 2 values and a reduce function'
		StandardStream source = Streams.defer().sync().get()
		source.expectedAcceptCount = 2

		StandardStream reduced = source.reduce(new Reduction())

		when: 'the first value is accepted'
		source.accept(1)

		then: 'the reduced value is unknown'
		reduced.get() == null

		when: 'the second value is accepted'
		source.accept(2)

		then: 'the reduced value is known'
		reduced.get() == 2
	}

	def 'When an unknown number of values is being reduced, each reduction is made available'() {
		given: 'a composable with a reduce function'
		StandardStream source = Streams.defer().sync().get()
		StandardStream reduced = source.reduce(new Reduction())

		when: 'the first value is accepted'
		source.accept(1)

		then: 'the reduction is available'
		reduced.get() == 1

		when: 'the second value is accepted'
		source.accept(2)

		then: 'the updated reduction is available'
		reduced.get() == 2
	}

	def 'When an unknown number of values is being reduced, each reduction is passed to a consumer'() {
		given: 'a composable with a reduce function'
		StandardStream source = Streams.defer().sync().get()
		StandardStream reduced = source.reduce(new Reduction())
		def value
		reduced.consume(consumer { value = it })

		when: 'the first value is accepted'
		source.accept(1)

		then: 'the reduction is available'
		value == 1

		when: 'the second value is accepted'
		source.accept(2)

		then: 'the updated reduction is available'
		value == 2
	}

	def 'Reduce will accumulate a list of accepted values'() {
		given: 'a composable'
		StandardStream source = Streams.defer().sync().get()
		StandardStream reduced = source.reduce()

		when: 'the first value is accepted'
		source.accept(1)

		then: 'the list contains the first element'
		reduced.get() == [1]
	}

	def 'Reduce will accumulate a list of accepted values and pass it to a consumer'() {
		given: 'a source composable and a reduced composable'
		StandardStream source = Streams.defer().sync().get()
		StandardStream reduced = source.reduce()
		def value
		reduced.consume(consumer { value = it })

		when: 'the first value is accepted on the source'
		source.accept(1)

		then: 'the reduced list contains the first element'
		value == [1]

		when: 'the second value is accepted'
		source.accept(2)

		then: 'the reduced list contains the first and second elements'
		value == [1, 2]
	}

	def 'A composable can be mapped via an observable'() {
		given: 'a composable and an observable with a mapping function'
		StandardStream<Integer> source = Streams.defer().sync().get()

		Reactor reactor = Reactors.reactor().sync().get()

		reactor.receive($('key'), function({ Integer.toString(it.data) }))
		def value
		reactor.on($('key'), consumer { value = it.data })

		StandardStream<String> mapped = source.map('key', reactor)

		def mappedValue
		mapped.consume(consumer { mappedValue = it })

		when: 'the source accepts a value'
		source.accept(1)

		then: "the reactor's consumers are notified and the mapped composable is updated with the mapped value"
		value == 1

		// TODO mapped never accepts and values as no reply is being sent on the hidden replyTo key. It's not
		// clear from the javadoc how that reply is supposed to be sent.

		mappedValue == "1"
		mapped.get() == "1"
	}

	def 'An Observable can consume values from a Stream'() {
		given: 'a Stream and a Observable consumer'
		StandardStream composable = Streams.defer().sync().get()
		Observable observable = Mock(Observable)
		composable.consume('key', observable)

		when: 'the composable accepts a value'
		composable.accept(1)

		then: 'the observable is notified'
		1 * observable.notify('key', _)
	}

	def 'An observable can consume values from a Stream with a known set of values'() {
		given: 'a Stream with 3 values'
		StandardStream composable = Streams.each([1, 2, 3]).sync().get()
		Observable observable = Mock(Observable)

		when: 'a composable consumer is registerd'
		composable.consume('key', observable)

		composable.get()//  TODO This will pass if get is called, but I don't think it should be necessary

		then: 'the observable is notified of the values'
		3 * observable.notify('key', _)
	}

	static class Reduction implements Function<Reduction<Integer, Integer>, Integer> {
		@Override
		public Integer apply(Reduction<Integer, Integer> reduce) {
			def result = reduce.lastValue == null ? reduce.nextValue : reduce.lastValue * reduce.nextValue
			println "${reduce?.lastValue} ${reduce?.nextValue} reduced to ${result}"
			return result
		}
	}
}

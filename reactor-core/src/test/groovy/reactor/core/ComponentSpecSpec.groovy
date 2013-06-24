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

import reactor.P
import reactor.R
import reactor.S
import spock.lang.Specification

/**
 * @author Stephane Maldini
 */
class ComponentSpecSpec extends Specification {

	def "Reactor correctly built"() {

		when: "we create a plain Reactor"
		def reactor = R.reactor().sync().get()

		then:
		Reactor.isAssignableFrom(reactor.class)
	}

	def "Composable correctly built"() {

		when: "we create a plain Composable"
		def composable = S.defer().sync().get()
		composable.accept('test')

		then:
		StandardStream.isAssignableFrom(composable.class)
		composable.get() == 'test'

	}

	def "Promise correctly built"() {

		when: "we create a plain Promise"
		def promise = P.success('test').sync().get()

		then:
		StandardPromise.isAssignableFrom(promise.class)
		promise.get() == 'test'

	}

}


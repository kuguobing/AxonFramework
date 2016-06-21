/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

/**
 * @author Allard Buijze
 */
public class AbstractRepositoryTest {

    private AbstractRepository testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>>(JpaAggregate.class) {

            @Override
            protected AnnotatedAggregate<JpaAggregate> doCreateNew(Callable<JpaAggregate> factoryMethod) throws Exception {
                return new AnnotatedAggregate<>(factoryMethod.call(), aggregateModel(), null);
            }

            @Override
            protected void doSave(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected void doDelete(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected AnnotatedAggregate<JpaAggregate> doLoad(String aggregateIdentifier, Long expectedVersion) {
                return new AnnotatedAggregate<>(new JpaAggregate(), aggregateModel(), null);
            }
        };
        DefaultUnitOfWork.startAndGet(null);
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregateTypeVerification_CorrectType() throws Exception {
        testSubject.newInstance(() -> new JpaAggregate("hi"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateTypeVerification_WrongType() throws Exception {
        testSubject.newInstance(() -> "Not allowed");
    }
}
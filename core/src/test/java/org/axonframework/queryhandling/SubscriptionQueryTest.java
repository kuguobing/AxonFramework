/*
 * Copyright (c) 2010-2018. Axon Framework
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
 *
 *
 */

package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for subscription query functionality.
 *
 * @author Milan Savic
 */
public class SubscriptionQueryTest {

    private SimpleQueryBus queryBus = new SimpleQueryBus();
    private ChatQueryHandler chatQueryHandler = new ChatQueryHandler();
    private AnnotationQueryHandlerAdapter<ChatQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            chatQueryHandler);

    @Before
    public void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    public void testRegularQueryWithSubscriptionQueryHandler() throws ExecutionException, InterruptedException {
        // given
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("axonFrameworkCR",
                                          "subscribableChatMessages",
                                          ResponseTypes.multipleInstancesOf(String.class));

        // when
        List<String> messages = queryBus.query(queryMessage).thenApply(Message::getPayload).get();

        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), messages);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscriptionQueryWithRegularQueryHandler() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "chatMessages",
                                                      ResponseTypes.multipleInstancesOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onCompleted();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscriptionQueryWithSubscriptionQueryHandler() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "subscribableChatMessages",
                                                      ResponseTypes.multipleInstancesOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        chatQueryHandler.emitter.emit("Update1");
        Exception exception = new Exception("blah");
        chatQueryHandler.emitter.error(exception);
        chatQueryHandler.emitter.complete();
        try {
            chatQueryHandler.emitter.emit("Update2");
            fail("Once you close emitter, it shouldn't be possible to emit messages");
        } catch (CompletedEmitterException e) {
            // we want this to happen
        }

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onUpdate("Update1");
        verify(updateHandler, times(0)).onUpdate("Update2");
        verify(updateHandler).onCompleted();
        verify(updateHandler).onError(exception);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEmittingWhenRegistrationForSubscriptionQueryIsCanceled() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "subscribableChatMessages",
                                                      ResponseTypes.multipleInstancesOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        Registration registration = queryBus.subscriptionQuery(queryMessage, updateHandler);
        registration.cancel();
        try {
            chatQueryHandler.emitter.emit("Update");
            fail("It should not be possible to emit after registration for subscription query is canceled.");
        } catch (NoUpdateHandlerForEmitterException e) {
            // we want this to happen
        }

        // then
        verify(updateHandler, times(1)).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler, times(0)).onUpdate("Update");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscribingQueryHandlerFailing() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "failingQuery",
                                                      ResponseTypes.instanceOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<String, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onError(chatQueryHandler.toBeThrown);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateHandlerFailing() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "subscribableChatMessages",
                                                      ResponseTypes.multipleInstancesOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);
        doThrow(new RuntimeException()).when(updateHandler).onInitialResult(any());

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        chatQueryHandler.emitter.emit("Update");

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onUpdate("Update");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscriptionQueryWithInterceptors() {
        // given
        List<String> interceptedResponse = Arrays.asList("fakeReply1", "fakeReply2");
        queryBus.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return interceptedResponse;
            }
            return interceptorChain.proceed();
        });
        SubscriptionQueryMessage<String, List<String>, String> queryMessage =
                new GenericSubscriptionQueryMessage<>("axonFrameworkCR",
                                                      "subscribableChatMessages",
                                                      ResponseTypes.multipleInstancesOf(String.class),
                                                      ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onInitialResult(interceptedResponse);
    }

    @SuppressWarnings("unused")
    private class ChatQueryHandler {

        private QueryUpdateEmitter<String> emitter;
        private RuntimeException toBeThrown = new RuntimeException("oops");

        @QueryHandler(queryName = "subscribableChatMessages")
        public List<String> chatMessages(String chatRoom, QueryUpdateEmitter<String> emitter) {
            this.emitter = emitter;
            return Arrays.asList("Message1", "Message2", "Message3");
        }

        @QueryHandler(queryName = "chatMessages")
        public List<String> chatMessages(String chatRoom) {
            return Arrays.asList("Message1", "Message2", "Message3");
        }

        @QueryHandler(queryName = "failingQuery")
        public String failingQuery(String criteria, QueryUpdateEmitter<String> emitter) {
            throw toBeThrown;
        }
    }
}

package org.pipelineframework.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PagedSourceContractTest {

    @Test
    void mappedPublisherForwardsOnlyDownstreamDemand() {
        DemandPublisher source = new DemandPublisher(List.of(1, 2, 3));
        Flow.Publisher<String> mapped = PagedSourcePublishers.map(source, Object::toString);
        TestSubscriber<String> subscriber = new TestSubscriber<>();

        mapped.subscribe(subscriber);
        assertTrue(subscriber.items.isEmpty());
        assertEquals(0, source.requested.get());

        subscriber.subscription.request(2);
        assertEquals(List.of("1", "2"), subscriber.items);
        assertEquals(2, source.requested.get());
        assertFalse(subscriber.completed);
    }

    @Test
    void mapperFailureCancelsTheProviderPublisher() {
        DemandPublisher source = new DemandPublisher(List.of(1));
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        PagedSourcePublishers.<Integer, String>map(source,
                ignored -> { throw new IllegalStateException("bad mapping"); })
            .subscribe(subscriber);

        subscriber.subscription.request(1);

        assertTrue(source.cancelled.get());
        assertTrue(subscriber.failure instanceof IllegalStateException);
    }

    @Test
    void nonExhaustedCompletionMustAdvanceEvenWhenItEmitsNoItems() {
        PagedSourceRequest<String> initial = new PagedSourceRequest<>("input", "source", Optional.empty(), 10);
        new PagedSourceCompletion(0, Optional.of("next"), false).validateAgainst(initial);

        PagedSourceRequest<String> resumed = new PagedSourceRequest<>(
            "input", "source", Optional.of("same"), 10);
        assertThrows(IllegalArgumentException.class,
            () -> new PagedSourceCompletion(0, Optional.of("same"), false).validateAgainst(resumed));
    }

    private static final class DemandPublisher implements Flow.Publisher<Integer> {
        private final List<Integer> values;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private DemandPublisher(List<Integer> values) { this.values = values; }

        @Override public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int index;
                @Override public void request(long n) {
                    requested.addAndGet(n);
                    while (!cancelled.get() && n-- > 0 && index < values.size()) {
                        subscriber.onNext(values.get(index++));
                    }
                    if (!cancelled.get() && index == values.size()) {
                        subscriber.onComplete();
                    }
                }
                @Override public void cancel() { cancelled.set(true); }
            });
        }
    }

    private static final class TestSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> items = new ArrayList<>();
        private Flow.Subscription subscription;
        private Throwable failure;
        private boolean completed;
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; }
        @Override public void onNext(T item) { items.add(item); }
        @Override public void onError(Throwable throwable) { failure = throwable; }
        @Override public void onComplete() { completed = true; }
    }
}

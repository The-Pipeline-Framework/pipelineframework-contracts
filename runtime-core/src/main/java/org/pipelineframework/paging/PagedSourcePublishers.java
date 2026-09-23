package org.pipelineframework.paging;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Demand-transparent adapters for portable paged publishers. */
public final class PagedSourcePublishers {
    private PagedSourcePublishers() {
    }

    public static <I, O> Flow.Publisher<O> map(
        Flow.Publisher<I> source,
        Function<? super I, ? extends O> mapper
    ) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        return downstream -> source.subscribe(new Flow.Subscriber<>() {
            private final AtomicBoolean terminated = new AtomicBoolean();
            private Flow.Subscription upstream;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                upstream = Objects.requireNonNull(subscription, "subscription must not be null");
                downstream.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { upstream.request(n); }
                    @Override public void cancel() {
                        if (terminated.compareAndSet(false, true)) {
                            upstream.cancel();
                        }
                    }
                });
            }

            @Override
            public void onNext(I item) {
                if (terminated.get()) {
                    return;
                }
                O mapped;
                try {
                    mapped = Objects.requireNonNull(mapper.apply(item), "paged source mapper returned null");
                } catch (Throwable failure) {
                    if (terminated.compareAndSet(false, true)) {
                        upstream.cancel();
                        downstream.onError(failure);
                    }
                    return;
                }
                downstream.onNext(mapped);
            }

            @Override
            public void onError(Throwable throwable) {
                if (terminated.compareAndSet(false, true)) {
                    downstream.onError(throwable);
                }
            }

            @Override
            public void onComplete() {
                if (terminated.compareAndSet(false, true)) {
                    downstream.onComplete();
                }
            }
        });
    }
}

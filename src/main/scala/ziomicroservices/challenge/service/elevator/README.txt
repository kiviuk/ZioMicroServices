• IO[E, A] - An effect that does not require any environment, may fail with an E, or may succeed with an A
• Task - An effect that does not require any environment, may fail with a Throwable, or may succeed with an A
• RIO - An effect that requires an environment of type R, may fail with a Throwable, or may succeed with an A.
• UIO - An effect that does not require any environment, cannot fail, and succeeds with an A
• URIO[R, A] - An effect that requires an environment of type R, cannot fail, and may succeed with an A.

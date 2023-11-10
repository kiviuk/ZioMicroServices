• IO[E, A] - An effect that does not require any environment, may fail with an E, or may succeed with an A
• Task - An effect that does not require any environment, may fail with a Throwable, or may succeed with an A
• RIO - An effect that requires an environment of type R, may fail with a Throwable, or may succeed with an A.
• UIO - An effect that does not require any environment, cannot fail, and succeeds with an A
• URIO[R, A] - An effect that requires an environment of type R, cannot fail, and may succeed with an A.


add status (still, running)

Idle: The elevator is not moving and no requests have been made.
Ascending: The elevator is moving upwards.
Descending: The elevator is moving downwards.
Door_Opening: The elevator doors are in the process of opening.
Door_Closing: The elevator doors are in the process of closing.
Loading_Passengers: The elevator is idle and the doors are open to allow passengers to enter or exit.
Out-of-order-failure
Out-of-order-maintenance

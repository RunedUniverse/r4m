# Maven Knowledge base

## Executions

+ a defined execution without a phase equates to phase `default`
+ an execution with prefix `default-` is defined through the lifecycle
+ executions defined through the lifecycle are dominant
+ when running a phase all executions of phases not matching the phase have their goals removed -> during mapping!

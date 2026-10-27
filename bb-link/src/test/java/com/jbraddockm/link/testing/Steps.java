package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.StepEffect;
import java.util.List;
import java.util.Optional;

/** Reads the steps of a conversation in tests without raw types. */
public final class Steps {

  private Steps() {}

  /**
   * The result of a step that finishes the conversation.
   *
   * @param step the step
   * @param <R> the kind of result
   * @return the result
   * @throws AssertionError if the step is not a finish
   */
  public static <R extends CommandResult> R finished(Step<R> step) {
    return switch (step) {
      case Step.Finish<R>(var result) -> result;
      case Step.Fail<R>(var error, _) ->
          throw new AssertionError("should have finished, but failed: " + error);
      case Step.Send<R>(var request, _) ->
          throw new AssertionError("should have finished, but asks: " + request.describe());
    };
  }

  /**
   * The error of a step that fails the conversation.
   *
   * @param step the step
   * @param <R> the kind of result
   * @return the error
   * @throws AssertionError if the step is not a failure
   */
  public static <R extends CommandResult> ServiceError failed(Step<R> step) {
    return switch (step) {
      case Step.Fail<R>(var error, _) -> error;
      case Step.Finish<R>(var result) ->
          throw new AssertionError("should have failed, but finished with " + result);
      case Step.Send<R>(var request, _) ->
          throw new AssertionError("should have failed, but asks: " + request.describe());
    };
  }

  /**
   * The request of a step that asks the device something.
   *
   * @param step the step
   * @param <R> the kind of result
   * @return the request
   * @throws AssertionError if the step is not a request
   */
  public static <R extends CommandResult> CgiRequest sent(Step<R> step) {
    return switch (step) {
      case Step.Send<R>(var request, _) -> request;
      case Step.Finish<R>(var result) ->
          throw new AssertionError("should have asked, but finished with " + result);
      case Step.Fail<R>(var error, _) ->
          throw new AssertionError("should have asked, but failed: " + error);
    };
  }

  /**
   * The partial result that a failing step carries.
   *
   * @param step the step
   * @param <R> the kind of result
   * @return what the step had so far, or nothing
   * @throws AssertionError if the step is not a failure
   */
  public static <R extends CommandResult> Optional<R> partial(Step<R> step) {
    return switch (step) {
      case Step.Fail<R>(_, var partial) -> partial;
      case Step.Finish<R>(var result) ->
          throw new AssertionError("should have failed, but finished with " + result);
      case Step.Send<R>(var request, _) ->
          throw new AssertionError("should have failed, but asks: " + request.describe());
    };
  }

  /**
   * The effects that a step asks to have applied before its request is sent.
   *
   * @param step the step
   * @param <R> the kind of result
   * @return the effects, in order
   * @throws AssertionError if the step is not a request
   */
  public static <R extends CommandResult> List<StepEffect> effects(Step<R> step) {
    return switch (step) {
      case Step.Send<R>(_, var before) -> before;
      case Step.Finish<R>(var result) ->
          throw new AssertionError("should have asked, but finished with " + result);
      case Step.Fail<R>(var error, _) ->
          throw new AssertionError("should have asked, but failed: " + error);
    };
  }
}

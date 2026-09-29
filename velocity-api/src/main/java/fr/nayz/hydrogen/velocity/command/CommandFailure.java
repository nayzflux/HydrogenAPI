package fr.nayz.hydrogen.velocity.command;

public final class CommandFailure extends RuntimeException {
    public CommandFailure(String message) {
        super(message);
    }
}

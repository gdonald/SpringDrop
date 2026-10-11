package dev.springdrop.kernel.status;

import java.util.List;

/** Contributes lines to the status report. A module adds checks by registering a bean of this type. */
public interface StatusCheck {

    List<Requirement> requirements();
}

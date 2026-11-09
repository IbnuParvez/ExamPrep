package com.examprep.api;

import java.util.UUID;

/** Resolves the authenticated student for the current request. */
public interface CurrentUserService {

    UUID requireCurrentUserId();
}

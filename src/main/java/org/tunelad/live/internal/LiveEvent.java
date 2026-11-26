package org.tunelad.live.internal;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a live event says: its type and, when it concerns one entity, the entity's id. Never the entity's content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record LiveEvent(String type, String id) {
}

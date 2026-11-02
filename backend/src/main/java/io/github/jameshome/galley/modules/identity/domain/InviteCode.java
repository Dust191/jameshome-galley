package io.github.jameshome.galley.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** 邀请码（领域对象）。三种无效码对外返回同一错误码，不区分原因（D-18）。 */
public record InviteCode(
        UUID id, String code, InviteStatus status, UUID createdBy, UUID usedBy, Instant usedAt) {

    public boolean usable() {
        return status == InviteStatus.UNUSED;
    }
}

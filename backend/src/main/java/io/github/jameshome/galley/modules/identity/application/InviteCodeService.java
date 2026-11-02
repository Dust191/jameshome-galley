package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.identity.domain.InviteCode;
import io.github.jameshome.galley.modules.identity.domain.InviteStatus;
import io.github.jameshome.galley.modules.identity.infrastructure.InviteCodeDO;
import io.github.jameshome.galley.modules.identity.infrastructure.InviteCodeMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 邀请码的签发与禁用。已使用与已禁用均为终态，不可撤销（D-13、D-18）。 */
@Service
public class InviteCodeService {

    private final InviteCodeMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public InviteCodeService(InviteCodeMapper mapper) {
        this.mapper = mapper;
    }

    public InviteCode create(UUID creatorId) {
        Instant now = Instant.now();
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = AuthService.randomCode(random);
            if (mapper.findByCode(code).isPresent()) {
                continue;
            }
            InviteCodeDO row = new InviteCodeDO();
            row.setId(UuidV7.generate());
            row.setCode(code);
            row.setStatus(InviteStatus.UNUSED.dbValue());
            row.setCreatedBy(creatorId);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            mapper.insert(row);
            return toDomain(row);
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码生成冲突，请重试");
    }

    public List<InviteCode> list(String status) {
        List<InviteCodeDO> rows = mapper.selectList(null);
        return rows.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().equals(status))
                .sorted(Comparator.comparing(InviteCodeDO::getCreatedAt).reversed())
                .map(InviteCodeService::toDomain)
                .toList();
    }

    public InviteCode disable(UUID id) {
        InviteCodeDO row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请码不存在");
        }
        if (!InviteStatus.UNUSED.dbValue().equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID, "只有未使用的邀请码可以禁用");
        }
        row.setStatus(InviteStatus.DISABLED.dbValue());
        row.setUpdatedAt(Instant.now());
        mapper.updateById(row);
        return toDomain(row);
    }

    static InviteCode toDomain(InviteCodeDO row) {
        return new InviteCode(
                row.getId(),
                row.getCode(),
                InviteStatus.fromDb(row.getStatus()),
                row.getCreatedBy(),
                row.getUsedBy(),
                row.getUsedAt());
    }
}

package com.campusguard.moderation.investigation;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A short database reservation around the model call. The acquisition
 * transaction ends before the network request so it holds no connection while
 * the model runs. An expired reservation can be taken after a process crash.
 */
@Service
public class InvestigationLease {
    private final JdbcTemplate jdbc;

    public InvestigationLease(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean acquire(UUID caseId, UUID owner) {
        return jdbc.update("""
                INSERT INTO investigation_leases (case_id, owner_token, lease_until)
                VALUES (?, ?, now() + interval '15 minutes')
                ON CONFLICT (case_id) DO UPDATE
                    SET owner_token = EXCLUDED.owner_token,
                        lease_until = EXCLUDED.lease_until
                WHERE investigation_leases.lease_until < now()
                """, caseId, owner) == 1;
    }

    @Transactional
    public void release(UUID caseId, UUID owner) {
        jdbc.update("DELETE FROM investigation_leases WHERE case_id = ? AND owner_token = ?",
                caseId, owner);
    }
}

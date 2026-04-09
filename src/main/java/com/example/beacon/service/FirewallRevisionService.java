package com.example.beacon.service;

import com.example.beacon.entity.FirewallSyncState;
import com.example.beacon.repository.FirewallSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FirewallRevisionService {

    private final FirewallSyncStateRepository firewallSyncStateRepository;

    @Transactional(readOnly = true)
    public long getCurrentRevision() {
        Long r = firewallSyncStateRepository.selectRevision(FirewallSyncState.SINGLETON_ID);
        return r != null ? r : 0L;
    }

    /**
     * 방화벽 규칙 정의가 바뀔 때마다 호출한다. 단조 증가 리비전을 반환한다.
     */
    @Transactional
    public long bumpRevision() {
        int updated = firewallSyncStateRepository.incrementRevisionById(FirewallSyncState.SINGLETON_ID);
        if (updated == 0) {
            if (!firewallSyncStateRepository.existsById(FirewallSyncState.SINGLETON_ID)) {
                firewallSyncStateRepository.save(FirewallSyncState.builder()
                        .id(FirewallSyncState.SINGLETON_ID)
                        .revision(1L)
                        .build());
                return 1L;
            }
        }
        Long r = firewallSyncStateRepository.selectRevision(FirewallSyncState.SINGLETON_ID);
        return r != null ? r : 0L;
    }
}

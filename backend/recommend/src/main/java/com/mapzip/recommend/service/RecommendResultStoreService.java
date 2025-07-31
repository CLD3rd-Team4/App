package com.mapzip.recommend.service;

import org.springframework.stereotype.Service;

import com.mapzip.recommend.dto.RecommendResultDto;
import com.mapzip.recommend.entity.RecommendResultEntity;
import com.mapzip.recommend.repository.RecommendResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendResultStoreService {

    private final RecommendResultRepository recommendResultRepository;

    public void save(RecommendResultDto dto) {
        RecommendResultEntity entity = RecommendResultEntity.fromDto(dto);
        recommendResultRepository.save(entity);
    }
}

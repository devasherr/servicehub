package com.servicehub.service;

import com.servicehub.dto.user.UserProfileResponse;
import com.servicehub.entity.User;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.mapper.UserMapper;
import com.servicehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserMapper.toProfile(user);
    }
}

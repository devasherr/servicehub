package com.servicehub.mapper;

import com.servicehub.dto.request.RequestCommentResponse;
import com.servicehub.dto.request.RequestUserSummary;
import com.servicehub.entity.RequestComment;
import com.servicehub.entity.User;

public final class RequestCommentMapper {

    private RequestCommentMapper() {
    }

    public static RequestCommentResponse toResponse(RequestComment comment) {
        return new RequestCommentResponse(
                comment.getId(),
                comment.getRequest().getId(),
                comment.getContent(),
                comment.isInternal(),
                toUserSummary(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    private static RequestUserSummary toUserSummary(User user) {
        return new RequestUserSummary(user.getId(), user.getEmail(), user.getFullName());
    }
}

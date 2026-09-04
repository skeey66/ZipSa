package com.zipsa.user;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 오퍼레이션 6 */
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findActive(userId);
        // TODO(커뮤니티·대출 도메인 구현 후): 최근 활동 3건을 실제 데이터로 채운다.
        //   type = LOAN_ESTIMATE | BOOKMARK | POST
        List<UserProfileResponse.RecentActivity> activities = List.of();
        return UserProfileResponse.of(user, activities);
    }

    /** 오퍼레이션 7 */
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findActive(userId);

        if (request.nickname() != null
                && !request.nickname().equals(user.getNickname())
                && userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        user.updateProfile(request.nickname(), request.ageRange(),
                request.maritalStatus(), request.job(), request.salaryRange(), request.region());

        return UserProfileResponse.of(user, List.of());
    }

    private User findActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}

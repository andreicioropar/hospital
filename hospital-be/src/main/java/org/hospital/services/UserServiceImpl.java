package org.hospital.services;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hospital.api.model.*;
import org.hospital.common.model.NotificationDetails;
import org.hospital.common.util.NotificationDetailsUtil;
import org.hospital.configuration.porperties.EmailNotificationProperties;
import org.hospital.errorhandling.Errors;
import org.hospital.errorhandling.UncheckedException;
import org.hospital.mappers.MedicMapper;
import org.hospital.mappers.PatientMapper;
import org.hospital.mappers.UserMapper;
import org.hospital.persistence.entity.*;
import org.hospital.persistence.repository.MedicRepository;
import org.hospital.persistence.repository.PatientRepository;
import org.hospital.persistence.repository.RoleRepository;
import org.hospital.persistence.repository.UserRepository;
import org.hospital.services.notification.dispatcher.NotificationDispatcherService;
import org.hospital.services.random.SecureRandomGeneratorService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hospital.common.model.NotificationDetails.REGISTER_LINK_KEY;

@Service
@Slf4j
@Validated
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MedicRepository medicRepository;
    private final PatientRepository patientRepository;
    private final UserMapper userMapper;
    private final MedicMapper medicMapper;
    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotificationProperties emailNotificationProperties;
    private final SecureRandomGeneratorService secureRandomGeneratorService;
    private final NotificationDispatcherService notificationDispatcherService;

    @Override
    public List<UserResponseModel> findAll() {
        return userRepository.findAll().stream()
                .map(userMapper::toUserModel)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponseModel findById(final Long id) {
        return userRepository.findById(id)
                .map(userMapper::toUserModel)
                .orElseThrow(() -> new UncheckedException(Errors.Functional.USER_NOT_FOUND));
    }

    @Override
    public UserResponseModel findByUsername(final String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toUserModel)
                .orElseThrow(() -> new UncheckedException(Errors.Functional.USER_NOT_FOUND));
    }

    @Override
    public UserResponseModel create(@Valid final UserRequestModel userDTO) {
        UserEntity userEntity = userMapper.toUserEntity(userDTO);

        Set<RoleEntity> roles = mapRoles(userDTO.getRoles());
        userEntity.setRoles(roles);

        userEntity.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        userEntity.setStatus(UserStatus.ACTIVE);

        UserEntity user = userRepository.saveAndFlush(userEntity);

        if (userDTO.getMedic() != null) {
            MedicEntity medic = medicMapper.toMedicEntity(userDTO.getMedic());
            medic.setUser(user);

            medicRepository.save(medic);
        }

        if (userDTO.getPatient() != null) {
            PatientEntity patient = patientMapper.toPatientEntity(userDTO.getPatient());
            patient.setUser(user);

            patientRepository.save(patient);
        }

        return userMapper.toUserModel(user);
    }

    @Override
    public UserRegisterStepOneResponseModel registerUserStepOne(final UserRegisterStepOneRequestModel userRegisterStepOneRequestModel) {
        UserEntity userEntity = userMapper.stepOneToUserEntity(userRegisterStepOneRequestModel);

        Set<RoleEntity> roles = mapRoles(userRegisterStepOneRequestModel.getRoles());
        userEntity.setRoles(roles);

        userEntity.setPassword(passwordEncoder.encode(userRegisterStepOneRequestModel.getPassword()));
        userEntity.setStatus(UserStatus.WAITING_CONFIRMATION);

        //TODO Create a registerTokenRepository then save this token and add an expiration date on the token
        //TODO Later on stepTwo verify the expiration token when accessing the link
        final var token = secureRandomGeneratorService.generateRandomString();

        sendRegistrationEmail(userRegisterStepOneRequestModel, token);

        userRepository.save(userEntity);

        return userMapper.stepOneToUserModel(userEntity);
    }

    @Override
    public void deleteById(final Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public UserResponseModel update(final Long id, final UserUpdateRequestModel userModel) {
        UserEntity existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UncheckedException(Errors.Functional.USER_NOT_FOUND));

        userMapper.updateUserEntity(existingUser, userModel);

        UserEntity savedUser = userRepository.save(existingUser);

        return userMapper.toUserModel(savedUser);
    }

    private Set<RoleEntity> mapRoles(Set<String> roles) {
        return roles
                .stream()
                .map(roleRepository::findByName)
                .map(roleEntity -> roleEntity.orElseThrow(() -> new UncheckedException(Errors.Functional.ROLE_NOT_FOUND)))
                .collect(Collectors.toSet());
    }

    private void sendRegistrationEmail(UserRegisterStepOneRequestModel userRegisterStepOneRequestModel, String token) {
        final var notificationRegisterLink = NotificationDetailsUtil.getAdditionalProperty(
                emailNotificationProperties,
                NotificationDetails.NotificationType.REGISTER_ACCOUNT,
                REGISTER_LINK_KEY
                ).replace(NotificationDetails.TOKEN_PLACEHOLDER_KEY, token);

        final var notificationDetails = new NotificationDetails(NotificationDetails.NotificationType.REGISTER_ACCOUNT)
                .addKeyValueItem(NotificationDetails.TO_KEY, userRegisterStepOneRequestModel.getEmail())
                .addKeyValueItem(NotificationDetails.USERNAME_KEY, userRegisterStepOneRequestModel.getUsername())
                .addKeyValueItem(REGISTER_LINK_KEY, notificationRegisterLink);

        log.info("Send register email to: {}", userRegisterStepOneRequestModel.getEmail());

        try {
            notificationDispatcherService.dispatchNotification(notificationDetails);
            log.info("Notification successfully sent to: {}", userRegisterStepOneRequestModel.getEmail());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

}

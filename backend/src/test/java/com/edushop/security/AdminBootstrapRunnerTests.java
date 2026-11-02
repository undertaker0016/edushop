package com.edushop.security;

import com.edushop.entity.Role;
import com.edushop.entity.User;
import com.edushop.repository.RoleRepository;
import com.edushop.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private Role adminRole;

	@Test
	void doesNothingWhenBootstrapCredentialsAreNotProvided() throws Exception {
		AdminBootstrapRunner runner = newRunner("", "");

		runner.run(new DefaultApplicationArguments(new String[0]));

		verifyNoInteractions(roleRepository, userRepository, passwordEncoder);
	}

	@Test
	void createsHashedAdminOnlyWhenExplicitCredentialsAreProvided() throws Exception {
		when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
		when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("correct-horse-battery-staple"))
				.thenReturn("{bcrypt}encoded-password-hash");
		when(userRepository.saveAndFlush(any(User.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		AdminBootstrapRunner runner = newRunner("admin@example.test", "correct-horse-battery-staple");
		runner.run(new DefaultApplicationArguments(new String[0]));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		User createdAdmin = userCaptor.getValue();
		assertThat(createdAdmin.getEmail()).isEqualTo("admin@example.test");
		assertThat(createdAdmin.getPasswordHash()).isEqualTo("{bcrypt}encoded-password-hash");
		assertThat(createdAdmin.getRoles()).containsExactly(adminRole);
	}

	@Test
	void refusesToElevateAnExistingNonAdminAccount() {
		Role instructorRole = org.mockito.Mockito.mock(Role.class);
		User existingUser = org.mockito.Mockito.mock(User.class);
		when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
		when(userRepository.findByEmail("existing@example.test")).thenReturn(Optional.of(existingUser));
		when(existingUser.getRoles()).thenReturn(Set.of(instructorRole));
		when(instructorRole.getName()).thenReturn("INSTRUCTOR");

		AdminBootstrapRunner runner = newRunner("existing@example.test", "correct-horse-battery-staple");

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("refusing to elevate");
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	private AdminBootstrapRunner newRunner(String email, String password) {
		return new AdminBootstrapRunner(
				roleRepository,
				userRepository,
				passwordEncoder,
				email,
				password,
				"EduShop Administrator");
	}
}

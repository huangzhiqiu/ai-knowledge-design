# Testing Guidelines

> Best practices for unit testing, integration testing, and test automation in CBOL messaging system.

## Testing Strategy

### Test Pyramid

```
                    /\
                   /  \
                  / E2E \          < 5% of tests
                 /--------\
                / Integration \     ~15% of tests
               /----------------\
              /   Unit Tests      \   ~80% of tests
             /----------------------\
```

| Test Type | Purpose | Tools | Execution Time |
|-----------|---------|-------|---------------|
| Unit | Test individual classes/methods in isolation | JUnit 5 + Mockito | < 10ms each |
| Integration | Test interactions between components | JUnit 5 + Spring Boot Test + Testcontainers | 100ms - 5s |
| E2E | Test complete user flows | REST Assured / Selenium / Playwright | 5s - 60s |
| Performance | Test throughput and latency | JMeter / Gatling / k6 | Minutes |

### Test Naming Conventions

```java
// ✅ Good - descriptive test names (methodName_shouldExpectedBehavior_whenCondition)
@Test
void sendMessage_shouldPersistMessage_whenValidRequest() { }

@Test
void sendMessage_shouldThrowValidationException_whenContentBlank() { }

@Test
void sendMessage_shouldReturn201_whenMessageSentSuccessfully() { }

@Test
void getConversation_shouldReturn404_whenConversationNotFound() { }

// ❌ Bad - vague names
@Test
void test1() { }

@Test
void testSendMessage() { }

@Test
void test() { }
```

## Unit Testing

### JUnit 5 Best Practices

```java
// ✅ Good - JUnit 5 with proper structure
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        // Common setup for all tests
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("should persist message when valid request")
        void shouldPersistMessage_whenValidRequest() {
            // Given
            SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(1L)
                .content("Hello")
                .build();
            Conversation conversation = Conversation.builder().id(1L).status(ACTIVE).build();
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            MessageResponse response = messageService.sendMessage(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo("Hello");
            verify(messageRepository).save(any(Message.class));
            verifyNoMoreInteractions(messageRepository);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when conversation not found")
        void shouldThrowResourceNotFoundException_whenConversationNotFound() {
            // Given
            SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(999L).content("Hello").build();
            when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() -> messageService.sendMessage(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conversation")
                .hasMessageContaining("999");
            verify(messageRepository, never()).save(any());
        }
    }
}
```

### Mockito Best Practices

```java
// ✅ Good - use @Mock + @InjectMocks
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;
}

// ✅ Good - use argument matchers
when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
when(userRepository.save(argThat(u -> u.getEmail().contains("@")))).thenReturn(user);

// ✅ Good - verify interactions
verify(userRepository).findById(1L);
verify(userRepository, times(1)).save(any());
verify(userRepository, never()).delete(any());
verifyNoMoreInteractions(userRepository);

// ❌ Bad - mocking value objects, using real new in test
when(new User()).thenReturn(mockUser);  // can't mock constructors without mockito-inline
User user = new User();  // should use builder or test fixture

// ❌ Bad - over-mocking (mock everything including the class under test)
@Mock private UserService userService;  // don't mock the class under test!
```

### AssertJ Best Practices

```java
// ✅ Good - AssertJ fluent assertions
assertThat(response)
    .isNotNull()
    .isInstanceOf(MessageResponse.class)
    .satisfies(r -> {
        assertThat(r.getId()).isPositive();
        assertThat(r.getContent()).isEqualTo("Hello");
        assertThat(r.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(r.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    });

assertThat(messages)
    .hasSize(3)
    .extracting(Message::getContent)
    .containsExactly("Hello", "World", "!")
    .doesNotContainNull();

assertThatThrownBy(() -> service.sendMessage(invalidRequest))
    .isInstanceOf(ValidationException.class)
    .hasMessageContaining("content")
    .hasNoCause();

// ❌ Bad - JUnit assertions (less readable)
assertNotNull(response);
assertEquals("Hello", response.getContent());
assertEquals(MessageStatus.SENT, response.getStatus());
```

### Test Data Builders

```java
// ✅ Good - test data builder with defaults
public class MessageTestBuilder {
    private Long id = 1L;
    private Long conversationId = 1L;
    private Long senderId = 1L;
    private String content = "Test message";
    private MessageStatus status = MessageStatus.SENT;
    private Instant createdAt = Instant.now();

    public static MessageTestBuilder aMessage() {
        return new MessageTestBuilder();
    }

    public MessageTestBuilder withId(Long id) { this.id = id; return this; }
    public MessageTestBuilder withContent(String content) { this.content = content; return this; }
    public MessageTestBuilder withStatus(MessageStatus status) { this.status = status; return this; }

    public Message build() {
        return Message.builder()
            .id(id).conversationId(conversationId).senderId(senderId)
            .content(content).status(status).createdAt(createdAt)
            .build();
    }
}

// Usage
Message message = MessageTestBuilder.aMessage()
    .withContent("Custom content")
    .withStatus(MessageStatus.DELIVERED)
    .build();
```

## Integration Testing

### Spring Boot Test

```java
// ✅ Good - integration test with Spring Boot Test
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/messages should return 201")
    void shouldReturn201_whenSendMessage() throws Exception {
        SendMessageRequest request = SendMessageRequest.builder()
            .conversationId(1L).content("Hello").build();

        mockMvc.perform(post("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer " + getValidToken()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.content").value("Hello"))
            .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/messages/{id} should return 200")
    void shouldReturn200_whenGetMessage() throws Exception {
        mockMvc.perform(get("/api/v1/messages/1")
                .header("Authorization", "Bearer " + getValidToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/messages should return 400 when content blank")
    void shouldReturn400_whenContentBlank() throws Exception {
        SendMessageRequest request = SendMessageRequest.builder()
            .conversationId(1L).content("").build();

        mockMvc.perform(post("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer " + getValidToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
```

### Testcontainers

```java
// ✅ Good - Testcontainers for real database
@Testcontainers
@SpringBootTest
class MessageRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("cbol_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MessageRepository messageRepository;

    @Test
    @DisplayName("should save and retrieve message")
    void shouldSaveAndRetrieveMessage() {
        Message message = MessageTestBuilder.aMessage().build();
        Message saved = messageRepository.save(message);

        Optional<Message> found = messageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Test message");
    }
}
```

## Test Coverage

### Coverage Targets

| Component | Line Coverage | Branch Coverage |
|-----------|--------------|----------------|
| Domain services | >= 90% | >= 80% |
| Application services | >= 85% | >= 75% |
| Controllers | >= 80% | >= 70% |
| Repository (custom) | >= 80% | >= 70% |
| Configuration | >= 50% | >= 40% |
| DTOs/Entities | >= 30% | >= 20% |
| **Overall** | **>= 80%** | **>= 70%** |

### JaCoCo Configuration

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                        <excludes>
                            <exclude>**/config/**</exclude>
                            <exclude>**/dto/**</exclude>
                            <exclude>**/entity/**</exclude>
                            <exclude>**/*Application.java</exclude>
                        </excludes>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Performance Testing

### Gatling Setup

```scala
// src/test/scala/com/selfdevelopment/cbol/MessageSimulation.scala
class MessageSimulation extends Simulation {

    val httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    val sendMessage = exec(
        http("Send Message")
            .post("/api/v1/messages")
            .header("Authorization", "Bearer ${token}")
            .body(StringBody("""{"conversationId":1,"content":"Hello ${counter}"}"""))
            .check(status.is(201))
    )

    val scn = scenario("Message Load Test")
        .exec(session -> session.set("token", getAuthToken()))
        .repeat(100) {
            exec(session -> session.set("counter", System.currentTimeMillis()))
            .exec(sendMessage)
            .pause(100.milliseconds)
        }

    setUp(
        scn.injectOpen(
            rampUsers(100).during(10.seconds),  // ramp up to 100 users
            constantUsersPerSec(50).during(60.seconds)  // sustain 50 req/s
        )
    ).protocols(httpProtocol)
     .assertions(
        global.responseTime.max.lt(500),       // max response time < 500ms
        global.responseTime.percentile3.lt(200), // p99 < 200ms
        global.successfulRequests.percent.gt(99)  // > 99% success
    )
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Tests depend on execution order | Flaky tests, false failures | Use `@TestMethodOrder` only when necessary, make tests independent |
| `@SpringBootTest` for unit tests | Slow startup, integration test overhead | Use plain JUnit + Mockito for unit tests |
| Mocking everything including domain objects | Tests don't test real behavior | Use real domain objects, mock only external dependencies |
| No assertions (only `assertNotNull`) | Doesn't verify behavior | Assert specific values, states, interactions |
| Testing implementation details | Brittle tests, refactor breaks tests | Test behavior/contract, not internal implementation |
| Hardcoded test data | Hard to maintain, not realistic | Use test data builders with sensible defaults |
| `Thread.sleep()` in tests | Flaky, slow | Use `Awaitility` or `CountDownLatch` for async testing |
| No cleanup after test | State leaks between tests | Use `@AfterEach` cleanup or `@Transactional` rollback |
| Testing only happy path | Edge cases not covered | Test boundary conditions, error paths, null inputs |
| Giant test methods (100+ lines) | Hard to understand, maintain | Split into multiple focused tests, use `@Nested` |
| Ignoring flaky tests (`@Disabled`) | Hidden bugs | Fix root cause, use `@Retry` or investigate race conditions |
| No parameterized tests | Duplicate test code | Use `@ParameterizedTest` with `@CsvSource`/`@MethodSource` |
| `System.out.println` in tests | Noisy output, not structured | Use AssertJ messages or SLF4J in test code |
| Testing private methods directly | Tests implementation, not behavior | Test through public API, or extract to package-private |

## References

- JUnit 5: https://junit.org/junit5/docs/current/user-guide/
- Mockito: https://site.mockito.org/
- AssertJ: https://assertj.github.io/doc/
- Testcontainers: https://testcontainers.com/
- Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing.html
- JaCoCo: https://www.jacoco.org/jacoco/trunk/doc/
- Gatling: https://gatling.io/docs/
- REST Assured: https://rest-assured.io/
- Testing on the Toilet (Google): https://testing.googleblog.com/

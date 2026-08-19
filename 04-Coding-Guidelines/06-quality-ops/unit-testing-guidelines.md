# Unit Testing Guidelines

> Comprehensive unit testing standards for CBOL Messaging Hub. Covers test principles, structure, mocking strategy, coverage, and common patterns.

## Core Principles

### FIRST Principles

Every unit test must satisfy:

| Principle | Description |
|-----------|-------------|
| **F**ast | Runs in < 10ms, no I/O, no network, no DB |
| **I**ndependent | No dependencies between tests, can run in any order |
| **R**epeatable | Same result every time, no flakiness, no randomness |
| **S**elf-validating | Asserts expected behavior, no manual inspection |
| **T**imely | Written before or alongside production code (TDD) |

### What is a Unit Test?

```
Unit Test = tests ONE class/method in ISOLATION
  ├── All dependencies are mocked or stubbed
  ├── No Spring context (@SpringBootTest is NOT unit test)
  ├── No database, no network, no file I/O
  ├── Runs in milliseconds
  └── Verifies behavior, not implementation
```

### Test Pyramid Distribution

```
                    /\
                   /  \
                  / E2E \          < 5%  (slow, brittle)
                 /--------\
                / Integration \     ~15%  (Spring context, Testcontainers)
               /----------------\
              /   Unit Tests      \   ~80%  (fast, isolated, majority)
             /----------------------\
```

## Test Structure

### AAA Pattern (Given-When-Then)

```java
// ✅ Good - clear AAA structure
@Test
void sendMessage_shouldReturnMessageResponse_whenValidRequest() {
    // Given (Arrange)
    SendMessageRequest request = SendMessageRequest.builder()
        .conversationId(1L)
        .content("Hello")
        .build();
    Conversation conversation = Conversation.builder().id(1L).status(ACTIVE).build();
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
    when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

    // When (Act)
    MessageResponse response = messageService.sendMessage(request);

    // Then (Assert)
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isEqualTo("Hello");
    assertThat(response.getStatus()).isEqualTo(MessageStatus.SENT);
    verify(messageRepository).save(any(Message.class));
}

// ❌ Bad - no structure, everything mixed
@Test
void testSend() {
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(new Conversation()));
    MessageResponse r = messageService.sendMessage(new SendMessageRequest(1L, "Hello"));
    assertNotNull(r);
    assertEquals("Hello", r.getContent());
    // no verify, no clear sections
}
```

### One Assertion Concept Per Test

```java
// ✅ Good - one behavior per test
@Test
void sendMessage_shouldPersistMessage_whenValidRequest() {
    // ... setup
    messageService.sendMessage(request);
    verify(messageRepository).save(any(Message.class));
}

@Test
void sendMessage_shouldReturnCreatedMessage_whenValidRequest() {
    // ... setup
    MessageResponse response = messageService.sendMessage(request);
    assertThat(response.getContent()).isEqualTo("Hello");
}

@Test
void sendMessage_shouldThrowResourceNotFound_whenConversationMissing() {
    // ... setup
    assertThatThrownBy(() -> messageService.sendMessage(request))
        .isInstanceOf(ResourceNotFoundException.class);
}

// ❌ Bad - multiple unrelated assertions in one test
@Test
void testSendMessage() {
    MessageResponse r = messageService.sendMessage(request);
    assertEquals("Hello", r.getContent());
    verify(messageRepository).save(any());
    // if first assert fails, we don't know if save was called
}
```

## Naming Conventions

### Test Class Naming

```java
// ✅ Good - {ClassUnderTest}Test
class MessageServiceTest { }
class ConversationRepositoryTest { }
class MessageControllerTest { }
class MessageValidatorTest { }

// ❌ Bad
class TestMessageService { }  // wrong prefix
class MessageServiceTests { } // plural
class MessageServiceTestClass { } // redundant
```

### Test Method Naming

```java
// ✅ Good - methodUnderTest_shouldExpectedBehavior_whenCondition
@Test
void sendMessage_shouldPersistMessage_whenValidRequest() { }

@Test
void sendMessage_shouldThrowValidationException_whenContentBlank() { }

@Test
void sendMessage_shouldThrowResourceNotFound_whenConversationNotExists() { }

@Test
void getConversation_shouldReturn404_whenConversationNotFound() { }

@Test
void deleteMessage_shouldNotDelete_whenUserIsNotOwner() { }

// Alternative: should{Behavior}_when{Condition}
@Test
void shouldPersistMessage_whenValidRequest() { }

// ❌ Bad - vague names
@Test
void test1() { }
@Test
void testSendMessage() { }
@Test
void test() { }
@Test
void sendMessageTest() { }
```

### Display Names

```java
// ✅ Good - use @DisplayName for human-readable descriptions
@Nested
@DisplayName("sendMessage")
class SendMessage {

    @Test
    @DisplayName("should persist message when valid request")
    void shouldPersistMessage_whenValidRequest() { }

    @Test
    @DisplayName("should throw ResourceNotFoundException when conversation not found")
    void shouldThrowResourceNotFound_whenConversationNotFound() { }
}
```

## Mocking Strategy

### What to Mock

| Mock | Don't Mock |
|------|------------|
| External services (REST, SOAP) | Domain entities / value objects |
| Database repositories | DTOs / request/response objects |
| Message queue producers/consumers | Enums |
| Cache (Redis) clients | Simple utility classes (pure functions) |
| Third-party APIs | The class under test |
| Time/Clock (for deterministic tests) | Collections / standard library |

### Mockito Best Practices

```java
// ✅ Good - @ExtendWith(MockitoExtension.class) + @Mock + @InjectMocks
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageService messageService;
}

// ✅ Good - strict stubbing (default in Mockito 5+)
@ExtendWith(MockitoExtension.class)  // strict stubs by default
class MessageServiceTest { }

// ✅ Good - use lenient() only when necessary
@Mock(lenient = true)
private MessageRepository messageRepository;

// ✅ Good - argument matchers
when(messageRepository.findById(anyLong())).thenReturn(Optional.of(message));
when(messageRepository.save(argThat(m -> m.getContent().contains("Hello")))).thenReturn(message);
when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

// ✅ Good - verify interactions
verify(messageRepository).save(any(Message.class));
verify(messageRepository, times(1)).findById(1L);
verify(messageRepository, never()).delete(any());
verify(messageRepository, atLeastOnce()).save(any());
verifyNoMoreInteractions(messageRepository);
verifyNoInteractions(messageRepository);

// ❌ Bad - mocking the class under test
@Mock
private MessageService messageService;  // NEVER mock the class under test!

// ❌ Bad - mocking value objects
@Mock
private Message message;  // use real builder instead

// ❌ Bad - too many mocks (test is not testing real behavior)
@Mock private Repo1 repo1;
@Mock private Repo2 repo2;
@Mock private Service1 service1;
@Mock private Service2 service2;
@Mock private Mapper1 mapper1;
// ... 10+ mocks = class has too many dependencies, refactor!
```

### Mock Return Values

```java
// ✅ Good - use thenReturn for simple values
when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

// ✅ Good - use thenAnswer for dynamic behavior
when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
    Message saved = invocation.getArgument(0);
    saved.setId(1L);  // simulate DB generating ID
    return saved;
});

// ✅ Good - use thenThrow for exceptions
when(messageRepository.findById(999L)).thenThrow(new DataAccessException("DB error") {});

// ✅ Good - consecutive calls
when(messageRepository.findById(1L))
    .thenReturn(Optional.empty())  // first call
    .thenReturn(Optional.of(message));  // second call

// ❌ Bad - using doReturn when not necessary (less type-safe)
doReturn(Optional.of(message)).when(messageRepository).findById(1L);
// Use when(...).thenReturn(...) instead, unless mocking void methods or spies
```

### Void Methods

```java
// ✅ Good - mock void methods with doNothing/doThrow
doNothing().when(messageRepository).delete(any());
doThrow(new DataAccessException("DB error") {}).when(messageRepository).delete(any());

// ✅ Good - verify void method was called
messageService.deleteMessage(1L);
verify(messageRepository).deleteById(1L);
```

### Spies (Partial Mocks)

```java
// ✅ Good - use spy when you need real behavior for some methods
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Spy
    private MessageRepository messageRepository;  // real object, can stub specific methods

    @Test
    void shouldUseRealFindById_whenNotStubbed() {
        // findById uses real implementation
        // save can be stubbed if needed
        doReturn(message).when(messageRepository).save(any());
    }
}

// ❌ Bad - overusing spies (sign of bad design)
// If you need to spy the class under test, the class has too many responsibilities
```

## Assertions

### AssertJ (Preferred)

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

// ✅ Good - collection assertions
assertThat(messages)
    .hasSize(3)
    .isNotEmpty()
    .extracting(Message::getContent)
    .containsExactly("Hello", "World", "!")
    .contains("Hello")
    .doesNotContainNull()
    .doesNotHaveDuplicates();

// ✅ Good - exception assertions
assertThatThrownBy(() -> messageService.sendMessage(invalidRequest))
    .isInstanceOf(ValidationException.class)
    .hasMessageContaining("content")
    .hasNoCause();

// ✅ Good - optional assertions
assertThat(result)
    .isPresent()
    .hasValueSatisfying(m -> assertThat(m.getContent()).isEqualTo("Hello"));

// ✅ Good - field by field comparison
assertThat(savedMessage)
    .usingRecursiveComparison()
    .ignoringFields("id", "createdAt")
    .isEqualTo(expectedMessage);

// ❌ Bad - JUnit assertions (less readable, no fluent API)
assertNotNull(response);
assertEquals("Hello", response.getContent());
assertEquals(MessageStatus.SENT, response.getStatus());
assertTrue(response.getId() > 0);
```

### Assertion Rules

```java
// ✅ Good - assert the specific behavior, not just "not null"
@Test
void sendMessage_shouldSetStatusToSent_whenValidRequest() {
    MessageResponse response = messageService.sendMessage(request);
    assertThat(response.getStatus()).isEqualTo(MessageStatus.SENT);  // specific
}

// ❌ Bad - only assertNotNull (doesn't verify behavior)
@Test
void sendMessage_shouldWork() {
    MessageResponse response = messageService.sendMessage(request);
    assertNotNull(response);  // not enough!
}

// ✅ Good - verify both return value AND side effects
@Test
void sendMessage_shouldPersistAndReturnMessage_whenValidRequest() {
    MessageResponse response = messageService.sendMessage(request);

    assertThat(response.getContent()).isEqualTo("Hello");  // return value
    verify(messageRepository).save(any(Message.class));  // side effect
}
```

## Parameterized Tests

```java
// ✅ Good - @ParameterizedTest with @CsvSource
@ParameterizedTest
@CsvSource({
    "Hello, true",
    "'', false",
    "'   ', false",
    "null, false"
})
void shouldValidateContent_whenContentGiven(String content, boolean expectedValid) {
    boolean result = messageValidator.isValidContent(content);
    assertThat(result).isEqualTo(expectedValid);
}

// ✅ Good - @MethodSource for complex arguments
@ParameterizedTest
@MethodSource("invalidContentProvider")
void shouldThrowValidationException_whenInvalidContent(String content, String errorMessage) {
    SendMessageRequest request = SendMessageRequest.builder()
        .conversationId(1L).content(content).build();

    assertThatThrownBy(() -> messageService.sendMessage(request))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining(errorMessage);
}

static Stream<Arguments> invalidContentProvider() {
    return Stream.of(
        Arguments.of("", "content must not be blank"),
        Arguments.of("   ", "content must not be blank"),
        Arguments.of("a".repeat(5001), "content must be at most 5000 characters")
    );
}

// ✅ Good - @EnumSource for enum values
@ParameterizedTest
@EnumSource(MessageStatus.class)
void shouldHandleAllMessageStatuses(MessageStatus status) {
    Message message = MessageTestBuilder.aMessage().withStatus(status).build();
    // test that all statuses are handled
    assertThat(message.getStatus()).isNotNull();
}

// ✅ Good - @ValueSource for simple values
@ParameterizedTest
@ValueSource(strings = {"Hello", "World", "Test"})
void shouldAcceptValidContent(String content) {
    boolean result = messageValidator.isValidContent(content);
    assertThat(result).isTrue();
}
```

## Exception Testing

```java
// ✅ Good - assert exception type, message, and cause
@Test
void sendMessage_shouldThrowResourceNotFound_whenConversationNotExists() {
    when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

    SendMessageRequest request = SendMessageRequest.builder()
        .conversationId(999L).content("Hello").build();

    assertThatThrownBy(() -> messageService.sendMessage(request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Conversation")
        .hasMessageContaining("999")
        .hasNoCause();
}

// ✅ Good - assert exception with cause
@Test
void sendMessage_shouldWrapDataAccessException_whenDbFails() {
    when(conversationRepository.findById(1L))
        .thenThrow(new DataAccessException("Connection timeout") {});

    assertThatThrownBy(() -> messageService.sendMessage(request))
        .isInstanceOf(MessageSendException.class)
        .hasMessageContaining("Failed to send message")
        .hasCauseInstanceOf(DataAccessException.class);
}

// ✅ Good - verify no side effects on exception
@Test
void sendMessage_shouldNotPersistMessage_whenValidationFails() {
    SendMessageRequest invalidRequest = SendMessageRequest.builder()
        .conversationId(1L).content("").build();

    assertThatThrownBy(() -> messageService.sendMessage(invalidRequest))
        .isInstanceOf(ValidationException.class);

    verify(messageRepository, never()).save(any());  // no side effect
}

// ❌ Bad - using try-catch in tests
@Test
void testException() {
    try {
        messageService.sendMessage(invalidRequest);
        fail("Expected exception");  // easy to forget
    } catch (ValidationException e) {
        // assertion here
    }
}
```

## Test Organization

### @Nested Classes

```java
// ✅ Good - organize tests with @Nested
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @InjectMocks private MessageService messageService;

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("should persist message when valid request")
        void shouldPersistMessage_whenValidRequest() { }

        @Test
        @DisplayName("should throw ResourceNotFoundException when conversation not found")
        void shouldThrowResourceNotFound_whenConversationNotFound() { }

        @Test
        @DisplayName("should throw ValidationException when content blank")
        void shouldThrowValidationException_whenContentBlank() { }
    }

    @Nested
    @DisplayName("getMessage")
    class GetMessage {

        @Test
        @DisplayName("should return message when found")
        void shouldReturnMessage_whenFound() { }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowResourceNotFoundException_whenNotFound() { }
    }

    @Nested
    @DisplayName("deleteMessage")
    class DeleteMessage {

        @Test
        @DisplayName("should delete message when user is owner")
        void shouldDeleteMessage_whenUserIsOwner() { }

        @Test
        @DisplayName("should throw AccessDeniedException when user is not owner")
        void shouldThrowAccessDeniedException_whenUserIsNotOwner() { }
    }
}
```

### Test Lifecycle

```java
// ✅ Good - use @BeforeEach for common setup
@BeforeEach
void setUp() {
    // Reset mocks (MockitoExtension does this automatically)
    // Set up common test data
    validRequest = SendMessageRequest.builder()
        .conversationId(1L).content("Hello").build();
    conversation = Conversation.builder().id(1L).status(ACTIVE).build();
}

// ✅ Good - use @AfterEach for cleanup
@AfterEach
void tearDown() {
    // Clean up resources (not usually needed with MockitoExtension)
}

// ❌ Bad - @BeforeAll for mutable state (causes test interdependence)
@BeforeAll
static void setUpAll() {
    sharedState = new ArrayList<>();  // mutable shared state = bad!
}

// ✅ Good - @BeforeAll for immutable resources only
@BeforeAll
static void setUpAll() {
    // Initialize expensive immutable resources (e.g., test constants)
    // But prefer @BeforeEach for most cases
}
```

## Test Data Management

### Test Data Builders

```java
// ✅ Good - builder with sensible defaults
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
    public MessageTestBuilder withConversationId(Long id) { this.conversationId = id; return this; }

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

### Object Mother (For Complex Fixtures)

```java
// ✅ Good - Object Mother for common test scenarios
public class TestFixtures {

    public static Message sentTextMessage() {
        return MessageTestBuilder.aMessage()
            .withContent("Hello")
            .withStatus(MessageStatus.SENT)
            .build();
    }

    public static Message deliveredImageMessage() {
        return MessageTestBuilder.aMessage()
            .withContent("image_url.jpg")
            .withStatus(MessageStatus.DELIVERED)
            .build();
    }

    public static Conversation activeConversation() {
        return Conversation.builder()
            .id(1L)
            .status(ConversationStatus.ACTIVE)
            .ownerId(1L)
            .build();
    }

    public static SendMessageRequest validSendRequest() {
        return SendMessageRequest.builder()
            .conversationId(1L)
            .content("Hello")
            .contentType("TEXT")
            .build();
    }
}
```

## Coverage Strategy

### Coverage Targets by Component

| Component | Line Coverage | Branch Coverage | Notes |
|-----------|--------------|----------------|-------|
| Domain services | >= 90% | >= 80% | Core business logic |
| Application services | >= 85% | >= 75% | Orchestration logic |
| Controllers | >= 80% | >= 70% | Request handling |
| Repository (custom) | >= 80% | >= 70% | Custom queries |
| Validators | >= 95% | >= 90% | All validation paths |
| State machine | >= 95% | >= 90% | All transitions |
| Configuration | >= 50% | >= 40% | Spring config |
| DTOs/Entities | >= 30% | >= 20% | Getters/setters/equals |
| **Overall** | **>= 80%** | **>= 70%** | Minimum threshold |

### What to Cover

```java
// ✅ Good - test all branches
@Test
void shouldReturnTrue_whenContentIsValid() { }

@Test
void shouldReturnFalse_whenContentIsNull() { }

@Test
void shouldReturnFalse_whenContentIsEmpty() { }

@Test
void shouldReturnFalse_whenContentIsBlank() { }

@Test
void shouldReturnFalse_whenContentExceedsMaxLength() { }

// ✅ Good - test boundary conditions
@ParameterizedTest
@ValueSource(ints = {0, 1, 4999, 5000, 5001})
void shouldValidateContentLength(int length) {
    String content = "a".repeat(length);
    boolean expected = length >= 1 && length <= 5000;
    assertThat(validator.isValidContent(content)).isEqualTo(expected);
}

// ✅ Good - test error paths
@Test
void shouldThrowException_whenDbFails() { }

@Test
void shouldThrowException_whenExternalServiceTimeout() { }

@Test
void shouldThrowException_whenInvalidInput() { }
```

### Coverage Anti-Patterns

```java
// ❌ Bad - testing getters/setters for coverage
@Test
void testGetSetId() {
    Message m = new Message();
    m.setId(1L);
    assertEquals(1L, m.getId());  // trivial, no value
}

// ❌ Bad - testing implementation details
@Test
void shouldCallInternalHelperMethod() {
    // This tests HOW the method works, not WHAT it does
    // Refactoring the internal method breaks the test
}

// ❌ Bad - 100% coverage obsession
// Some code (e.g., exception classes, DTOs) doesn't need 100% coverage
// Focus on meaningful coverage of business logic
```

## Common Test Patterns

### Testing Async Code

```java
// ✅ Good - use CompletableFuture and Awaitility
@Test
void shouldProcessMessageAsync_whenMessageSent() throws Exception {
    // Given
    when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    CompletableFuture<MessageResponse> future = messageService.sendMessageAsync(request);

    // Then
    MessageResponse response = future.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatus()).isEqualTo(MessageStatus.SENT);
}

// ✅ Good - use Awaitility for async verification
@Test
void shouldEventuallyCallRepository_whenAsyncProcessing() {
    messageService.processAsync(message);

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(messageRepository).save(any()));
}

// ❌ Bad - Thread.sleep in tests
@Test
void shouldProcessAsync() throws InterruptedException {
    messageService.processAsync(message);
    Thread.sleep(1000);  // flaky! slow!
    verify(messageRepository).save(any());
}
```

### Testing Time-Dependent Code

```java
// ✅ Good - inject Clock for deterministic time
public class MessageService {
    private final Clock clock;  // inject Clock, don't use Instant.now() directly

    public MessageService(Clock clock) {
        this.clock = clock;
    }

    public Message createMessage(String content) {
        return Message.builder()
            .content(content)
            .createdAt(Instant.now(clock))  // use injected clock
            .build();
    }
}

// Test with fixed clock
@Test
void shouldSetCreatedAt_whenMessageCreated() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC);
    MessageService service = new MessageService(fixedClock);

    Message message = service.createMessage("Hello");

    assertThat(message.getCreatedAt()).isEqualTo(Instant.parse("2026-08-19T10:00:00Z"));
}

// ❌ Bad - using Instant.now() directly (non-deterministic)
public Message createMessage(String content) {
    return Message.builder()
        .content(content)
        .createdAt(Instant.now())  // can't test deterministically
        .build();
}
```

### Testing State Machines

```java
// ✅ Good - exhaustive state transition tests
@ExtendWith(MockitoExtension.class)
class ConversationStateMachineTest {

    private StateMachine<ConversationState, ConversationEvent, ConversationContext> stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ConversationStateMachineConfig().conversationStateMachine();
    }

    @Test
    @DisplayName("INIT + START_AI → AI_PROCESSING")
    void initToAiProcessing() {
        ConversationState result = stateMachine.fire(INIT, START_AI, mockContext());
        assertThat(result).isEqualTo(AI_PROCESSING);
    }

    @Test
    @DisplayName("Invalid transition throws exception")
    void invalidTransitionThrows() {
        assertThatThrownBy(() -> stateMachine.fire(INIT, CLOSE, mockContext()))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("All states have at least one outgoing transition")
    void allStatesHaveOutgoingTransitions() {
        for (ConversationState state : ConversationState.values()) {
            boolean hasOutgoing = Arrays.stream(ConversationEvent.values())
                .anyMatch(event -> {
                    try {
                        stateMachine.fire(state, event, mockContext());
                        return true;
                    } catch (IllegalStateTransitionException e) {
                        return false;
                    }
                });
            assertThat(hasOutgoing)
                .withFailMessage("State %s has no outgoing transitions", state)
                .isTrue();
        }
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Tests depend on execution order | Flaky tests, false failures | Make tests independent, no shared mutable state |
| `@SpringBootTest` for unit tests | Slow startup, integration overhead | Use plain JUnit + Mockito |
| Mocking everything including domain objects | Tests don't test real behavior | Use real domain objects, mock only external deps |
| No assertions (only `assertNotNull`) | Doesn't verify behavior | Assert specific values, states, interactions |
| Testing implementation details | Brittle tests, refactor breaks tests | Test behavior/contract, not internal implementation |
| Hardcoded test data everywhere | Hard to maintain, not realistic | Use test data builders with sensible defaults |
| `Thread.sleep()` in tests | Flaky, slow | Use Awaitility or CountDownLatch |
| No cleanup after test | State leaks between tests | Use @AfterEach or @Transactional rollback |
| Testing only happy path | Edge cases not covered | Test boundary conditions, error paths, null inputs |
| Giant test methods (100+ lines) | Hard to understand, maintain | Split into multiple focused tests, use @Nested |
| Ignoring flaky tests (`@Disabled`) | Hidden bugs | Fix root cause, investigate race conditions |
| No parameterized tests | Duplicate test code | Use @ParameterizedTest with @CsvSource/@MethodSource |
| `System.out.println` in tests | Noisy output | Use AssertJ messages or SLF4J |
| Testing private methods directly | Tests implementation, not behavior | Test through public API, or extract to package-private |
| Over-mocking (5+ mocks per test) | Class has too many dependencies | Refactor class, split responsibilities |
| `@SuppressWarnings("all")` in tests | Hides real issues | Suppress specific rules with justification |
| Copy-paste test code | Hard to maintain, inconsistent | Use test data builders, Object Mother, helper methods |
| Tests that hit real DB/network | Slow, flaky, environment-dependent | Use mocks for unit tests, Testcontainers for integration |
| Asserting on `toString()` | Brittle, format changes | Assert on specific fields |
| Not verifying mock interactions | Side effects not checked | Use verify() for important side effects |

## Maven Dependencies

```xml
<!-- pom.xml -->
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.25.3</version>
        <scope>test</scope>
    </dependency>

    <!-- Awaitility (async testing) -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <version>4.2.0</version>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers (integration tests) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>1.19.7</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Surefire (unit tests) -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                    <include>**/*Tests.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                    <exclude>**/*E2ETest.java</exclude>
                </excludes>
            </configuration>
        </plugin>

        <!-- Failsafe (integration tests) -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <includes>
                    <include>**/*IntegrationTest.java</include>
                    <include>**/*E2ETest.java</include>
                </includes>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## References

- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/
- Mockito Documentation: https://site.mockito.org/
- AssertJ Documentation: https://assertj.github.io/doc/
- Awaitility: https://github.com/awaitility/awaitility
- Testcontainers: https://testcontainers.com/
- Google Testing Blog: https://testing.googleblog.com/
- Test-Driven Development (Kent Beck): https://en.wikipedia.org/wiki/Test-driven_development
- FIRST Principles: https://pragprog.com/titles/utc2/the-way-of-the-web-tester/

---

*Last updated: 2026-08-19*

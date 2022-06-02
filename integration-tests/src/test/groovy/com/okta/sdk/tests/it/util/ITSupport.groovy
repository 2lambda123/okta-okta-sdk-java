/*
 * Copyright 2017 Okta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.sdk.tests.it.util

import com.okta.sdk.tests.ConditionalSkipTestAnalyzer
import com.okta.sdk.tests.Scenario
import org.openapitools.client.ApiClient
import org.openapitools.client.api.UserApi
import org.openapitools.client.model.CreateUserRequest
import org.openapitools.client.model.User
import org.openapitools.client.model.UserProfile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.testng.ITestContext
import org.testng.annotations.AfterSuite
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Listeners

@Listeners(value = ConditionalSkipTestAnalyzer.class)
abstract class ITSupport implements ClientProvider {

    private final static Logger log = LoggerFactory.getLogger(ITSupport)

    public static final USE_TEST_SERVER = "okta.use.testServer"
    public static final TEST_SERVER_ALL_SCENARIOS = "okta.testServer.allScenarios"
    public static final IT_OPERATION_DELAY = "okta.it.operationDelay"

    public static boolean isOIEEnvironment
    private TestServer testServer

    @BeforeSuite
    void start(ITestContext context) {

        boolean useTestServer = Boolean.getBoolean(USE_TEST_SERVER) ||
                Boolean.valueOf(System.getenv("OKTA_USE_TEST_SERVER"))

        if (useTestServer) {
            List<String> scenarios = []

            // if TEST_SERVER_ALL_SCENARIOS is set, we need to run all of them
            if (!Boolean.getBoolean(TEST_SERVER_ALL_SCENARIOS)) {
                context.getAllTestMethods().each { testMethod ->
                    Scenario scenario = testMethod.getConstructorOrMethod().getMethod().getAnnotation(Scenario)
                    if (scenario != null) {
                        scenarios.add(scenario.value())
                    }
                }
            }

            testServer = new TestServer().start(scenarios)
            System.setProperty(TestServer.TEST_SERVER_BASE_URL, "http://localhost:${testServer.getMockPort()}/")
        }

        isOIEEnvironment = isOIEEnvironment()
    }

    @AfterSuite()
    void stop() {
        if (testServer != null) {
            testServer.verify()
            testServer.stop()
        }
    }

    /**
     * Some Integration test operations exhibit flakiness at times due to replication lag at the core backend.
     * We therefore add delays between some test operations to ensure we give sufficient
     * interval between invoking operations on the backend resource.
     *
     * This delay could be specified by the following in order of precedence:
     * - System property 'okta.it.operationDelay'
     * - Env variable 'OKTA_IT_OPERATION_DELAY'
     */
    long getTestOperationDelay() {
        Long testDelay = Long.getLong(IT_OPERATION_DELAY)

        if (testDelay == null) {
            try {
                testDelay = Long.valueOf(System.getenv().getOrDefault("OKTA_IT_OPERATION_DELAY", "0"))
            } catch (NumberFormatException e) {
                log.error("Could not parse env variable OKTA_IT_OPERATION_DELAY. Will default to 0!")
                return 0
            }
        }

        return testDelay == null ? 0 : testDelay
    }

    User randomUser() {
        ApiClient client = getClient()

        def email = "joe.coder+" + UUID.randomUUID().toString() + "@example.com"

        CreateUserRequest createUserRequest = new CreateUserRequest();
        UserProfile userProfile = new UserProfile()
        userProfile.setFirstName("Joe")
        userProfile.setLastName("Coder")
        userProfile.setEmail(email)
        userProfile.setMobilePhone("1234567890")
        userProfile.setLogin(userProfile.getEmail())

        createUserRequest.setProfile(userProfile)
        UserApi userApi = new UserApi(client)
        User createdUser = userApi.createUser(createUserRequest, true, null, null)
        return createdUser
    }

//    Group randomGroup(String name = "java-sdk-it-${UUID.randomUUID().toString()}") {
//
//        Group group = GroupBuilder.instance()
//            .setName(name)
//            .setDescription(name)
//            .buildAndCreate(getClient())
//        registerForCleanup(group)
//
//        return group
//    }
//
//    PasswordPolicy randomPasswordPolicy(String groupId) {
//
//        PasswordPolicy policy = PasswordPolicyBuilder.instance()
//            .setName("java-sdk-it-" + UUID.randomUUID().toString())
//            .setStatus(Policy.StatusEnum.ACTIVE)
//            .setDescription("IT created Policy")
//            .setStatus(Policy.StatusEnum.ACTIVE)
//            .setPriority(1)
//            .addGroup(groupId)
//        .buildAndCreate(client)
//
//        registerForCleanup(policy)
//
//        return policy
//    }
//
//    OktaSignOnPolicy randomSignOnPolicy(String groupId) {
//
//        OktaSignOnPolicy policy = OktaSignOnPolicyBuilder.instance()
//            .setName("java-sdk-it-" + UUID.randomUUID().toString())
//            .setDescription("IT created Policy")
//            .setStatus(Policy.StatusEnum.ACTIVE)
//        .setType(PolicyType.OKTA_SIGN_ON)
//        .buildAndCreate(client)
//
//        registerForCleanup(policy)
//
//        return policy
//    }

    boolean isOIEEnvironment() {

        ResponseEntity<Map<String, Object>> responseEntity = getClient().invokeAPI("/.well-known/okta-organization",
            HttpMethod.GET,
            Collections.emptyMap(),
            null,
            null,
            new HttpHeaders(),
            new LinkedMultiValueMap<String, String>(),
            null,
            Collections.singletonList(MediaType.APPLICATION_JSON),
            null,
            new String[]{"api_token"},
            new ParameterizedTypeReference<Map<String, Object>>() {})

        if (responseEntity != null && responseEntity.getBody() != null) {
            String pipeline = responseEntity.getBody().get("pipeline")
            if (Objects.equals(pipeline, "idx")) {
                return true
            }
        }

        return false
    }
}
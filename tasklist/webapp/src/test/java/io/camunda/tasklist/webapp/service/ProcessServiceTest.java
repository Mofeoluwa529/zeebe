/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import io.camunda.zeebe.client.impl.command.CreateProcessInstanceCommandImpl;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessServiceTest {

  @Mock private TenantService tenantService;

  @Mock private ZeebeClient zeebeClient;

  @Spy
  private IdentityAuthorizationService identityAuthorizationService =
      new IdentityAuthorizationService();

  @InjectMocks private ProcessService instance;

  @Test
  void startProcessInstanceInvalidTenant() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    final String tenantId = "tenantA";

    final List<String> tenantIds = new ArrayList<String>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    final TenantService.AuthenticatedTenants authenticatedTenants =
        TenantService.AuthenticatedTenants.assignedTenants(tenantIds);

    doReturn(true).when(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);
    when(tenantService.isMultiTenancyEnabled()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void startProcessInstanceInvalidTenantMultiTenancyOff() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    final String tenantId = "tenantA";

    final List<String> tenantIds = new ArrayList<String>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    when(tenantService.isMultiTenancyEnabled()).thenReturn(false);
    doReturn(true).when(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId);
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  private ProcessInstanceEvent mockZeebeCreateProcessInstance(String processDefinitionKey) {
    final ProcessInstanceEvent processInstanceEvent = mock(ProcessInstanceEvent.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3 =
        mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);
    when(zeebeClient.newCreateInstanceCommand())
        .thenReturn(mock(CreateProcessInstanceCommandImpl.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey))
        .thenReturn(
            mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey).latestVersion())
        .thenReturn(step3);
    when(step3.send()).thenReturn(mock(ZeebeClientFutureImpl.class));
    when(step3.send().join()).thenReturn(processInstanceEvent);
    return processInstanceEvent;
  }

  private ProcessInstanceEvent mockZeebeCreateProcessInstanceNotFound(String processDefinitionKey) {
    final ProcessInstanceEvent processInstanceEvent = mock(ProcessInstanceEvent.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3 =
        mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);
    when(zeebeClient.newCreateInstanceCommand())
        .thenReturn(mock(CreateProcessInstanceCommandImpl.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey))
        .thenReturn(
            mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey).latestVersion())
        .thenReturn(step3);
    when(step3.send()).thenThrow(new ClientStatusException(Status.NOT_FOUND, null));
    return processInstanceEvent;
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(List.of("otherProcessDefinitionKey"))
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
  }

  @Test
  public void testStartProcessInstanceWhenProcessDefinitionNotFound() {
    // Given
    final String processDefinitionKey = "someKey";

    // When
    doReturn(List.of(processDefinitionKey))
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    mockZeebeCreateProcessInstanceNotFound(processDefinitionKey);

    // Then
    assertThatThrownBy(() -> instance.startProcessInstance(processDefinitionKey, ""))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessage("No process definition found with processDefinitionKey: 'someKey'");
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuthCaseHasNoPermissionOnAnyResource() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(Collections.emptyList())
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
  }

  @Test
  void startProcessInstanceWithResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(List.of("processDefinitionKey"))
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  void startProcessInstanceWithResourceBasedAuthCaseHasAllResourcesAccess() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(List.of("otherProcessDefinitionKey", "*"))
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
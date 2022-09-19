/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablePanel} from '../index';
import {
  render,
  screen,
  UserEvent,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';

import {LastModification} from 'App/ProcessInstance/LastModification';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createInstance} from 'modules/testUtils';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';

const editNameFromTextfieldAndBlur = async (user: UserEvent, value: string) => {
  const [nameField] = screen.getAllByTestId('new-variable-name');

  await user.click(nameField!);
  await user.type(nameField!, value);
  await user.tab();
};

const editValueFromTextfieldAndBlur = async (
  user: UserEvent,
  value: string
) => {
  const [valueField] = screen.getAllByTestId('new-variable-value');

  await user.click(valueField!);
  await user.type(valueField!, value);
  await user.tab();
};

const editValueFromJSONEditor = async (user: UserEvent, value: string) => {
  const [jsonEditor] = screen.getAllByTitle(/open json editor modal/i);
  await user.click(jsonEditor!);
  await user.click(screen.getByTestId('monaco-editor'));
  await user.type(screen.getByTestId('monaco-editor'), value);
  await user.click(screen.getByRole('button', {name: /apply/i}));
};

const editValue = async (type: string, user: UserEvent, value: string) => {
  if (type === 'textfield') {
    return editValueFromTextfieldAndBlur(user, value);
  }
  if (type === 'jsoneditor') {
    return editValueFromJSONEditor(user, value);
  }
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('New Variable Modifications', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              TEST_FLOW_NODE: 'COMPLETED',
              Activity_0qtp1k6: 'INCIDENT',
            })
          )
      )
    );

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      })
    );
    flowNodeStatesStore.fetchFlowNodeStates('instance_id');
  });

  afterEach(() => {
    variablesStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    processInstanceDetailsDiagramStore.reset();
    modificationsStore.reset();
    flowNodeStatesStore.reset();
  });

  it('should not create add variable modification if fields are empty', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    modificationsStore.enableModificationMode();
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();
    await user.click(screen.getByTestId('new-variable-name'));
    await user.tab();
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(modificationsStore.state.modifications.length).toBe(0);
  });

  it.each(['textfield', 'jsoneditor'])(
    'should not create add variable modification if name field is empty - %p',
    async (type) => {
      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      modificationsStore.enableModificationMode();
      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i})
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name')
      ).toBeInTheDocument();
      await user.click(screen.getByTestId('new-variable-name'));
      await user.tab();
      await editValue(type, user, '123');
      expect(
        screen.getByRole('button', {name: /add variable/i})
      ).toBeDisabled();
      expect(screen.getByText(/Name has to be filled/i)).toBeInTheDocument();
      expect(modificationsStore.state.modifications.length).toBe(0);
    }
  );

  it.each(['textfield', 'jsoneditor'])(
    'should not create add variable modification if name field is duplicate - %p',
    async (type) => {
      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      modificationsStore.enableModificationMode();
      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i})
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name')
      ).toBeInTheDocument();
      await editNameFromTextfieldAndBlur(user, 'test');
      await editValue(type, user, '123');
      expect(
        screen.getByRole('button', {name: /add variable/i})
      ).toBeDisabled();
      expect(screen.getByText(/Name should be unique/i)).toBeInTheDocument();
      expect(modificationsStore.state.modifications.length).toBe(0);

      await user.clear(screen.getByTestId('new-variable-name'));
      await editNameFromTextfieldAndBlur(user, 'test2');

      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '123',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      await editNameFromTextfieldAndBlur(user, 'test2');
      await editValue(type, user, '1234');
      expect(screen.getByText(/Name should be unique/i)).toBeInTheDocument();
      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '123',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);
    }
  );

  it('should not create add variable modification if value field is empty or invalid', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    modificationsStore.enableModificationMode();
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editNameFromTextfieldAndBlur(user, 'test2');
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(screen.getByText(/Value has to be filled/i)).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(0);
    await editValueFromTextfieldAndBlur(user, 'invalid value');
    expect(screen.getByText(/Value has to be JSON/i)).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(0);
  });

  it.each(['textfield', 'jsoneditor'])(
    'should create add variable modification on blur and update same modification if name or value is changed - %p',
    async (type) => {
      const {user} = render(
        <>
          <VariablePanel />
          <LastModification />
        </>,
        {wrapper: Wrapper}
      );
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      modificationsStore.enableModificationMode();
      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i})
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name')
      ).toBeInTheDocument();

      await editNameFromTextfieldAndBlur(user, 'test2');
      await editValue(type, user, '12345');

      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      await user.click(screen.getByTestId('new-variable-name'));
      await user.tab();
      await user.click(screen.getByTestId('new-variable-value'));
      await user.tab();

      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      await editNameFromTextfieldAndBlur(user, '-updated');

      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2-updated',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      expect(
        modificationsStore.getAddVariableModifications('instance_id')
      ).toEqual([
        {
          id: expect.any(String),
          name: 'test2-updated',
          value: '12345',
        },
      ]);

      await editValue(type, user, '678');
      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2-updated',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2-updated',
            newValue: '12345678',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      expect(
        modificationsStore.getAddVariableModifications('instance_id')
      ).toEqual([
        {
          id: expect.any(String),
          name: 'test2-updated',
          value: '12345678',
        },
      ]);

      await user.click(screen.getByRole('button', {name: 'Undo'}));

      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2-updated',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      expect(
        modificationsStore.getAddVariableModifications('instance_id')
      ).toEqual([
        {
          id: expect.any(String),
          name: 'test2-updated',
          value: '12345',
        },
      ]);
    }
  );

  it.each(['textfield', 'jsoneditor'])(
    'should not apply modification if value is the same as the last modification - %p',
    async (type) => {
      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      modificationsStore.enableModificationMode();
      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i})
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name')
      ).toBeInTheDocument();

      await user.click(screen.getByTestId('new-variable-name'));
      await user.tab();

      await user.click(screen.getByTestId('new-variable-value'));
      await user.tab();

      await user.clear(screen.getByTestId('new-variable-name'));
      await editNameFromTextfieldAndBlur(user, 'test2');
      await user.clear(screen.getByTestId('new-variable-value'));
      await editValue(type, user, '12345');

      expect(modificationsStore.state.modifications).toEqual([
        {
          payload: {
            flowNodeName: 'someProcessName',
            id: expect.any(String),
            name: 'test2',
            newValue: '12345',
            operation: 'ADD_VARIABLE',
            scopeId: 'instance_id',
          },
          type: 'variable',
        },
      ]);

      expect(
        modificationsStore.getAddVariableModifications('instance_id')
      ).toEqual([
        {
          id: expect.any(String),
          name: 'test2',
          value: '12345',
        },
      ]);
    }
  );
});

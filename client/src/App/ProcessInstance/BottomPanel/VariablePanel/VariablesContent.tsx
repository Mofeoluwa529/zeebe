/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import Variables from '../Variables';
import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {Form as ReactFinalForm} from 'react-final-form';
import {useNotifications} from 'modules/notifications';
import {VariableFormValues} from 'modules/types/variables';

import {
  Content,
  AddVariableButton,
  Form,
  VariablesContainer,
  EmptyPanel,
} from './styled';
import arrayMutators from 'final-form-arrays';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';

const VariablesContent: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const notifications = useNotifications();
  const {isModificationModeEnabled} = modificationsStore;

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

  const {displayStatus} = variablesStore;

  const hasEmptyNewVariable = (values: VariableFormValues) =>
    values.newVariables?.some(
      (variable) =>
        variable === undefined ||
        variable.name === undefined ||
        variable.value === undefined
    );

  return (
    <Content>
      {displayStatus === 'error' && (
        <StatusMessage variant="error">
          Variables could not be fetched
        </StatusMessage>
      )}
      {displayStatus === 'multi-instances' && (
        <StatusMessage variant="default">
          To view the Variables, select a single Flow Node Instance in the
          Instance History.
        </StatusMessage>
      )}
      {displayStatus === 'spinner' && (
        <EmptyPanel
          data-testid="variables-spinner"
          type="skeleton"
          Skeleton={SpinnerSkeleton}
        />
      )}

      <ReactFinalForm<VariableFormValues>
        mutators={{
          ...arrayMutators,
          triggerValidation(fieldsToValidate: string[], state, {changeValue}) {
            fieldsToValidate.forEach((fieldName) => {
              changeValue(state, fieldName, (n) => n);
            });
          },
        }}
        key={variablesStore.scopeId}
        onSubmit={async (values, form) => {
          const {initialValues} = form.getState();

          const {name, value} = values;

          if (name === undefined || value === undefined) {
            return;
          }

          const params = {
            id: processInstanceId,
            name,
            value,
            onSuccess: () => {
              notifications.displayNotification('success', {
                headline: 'Variable added',
              });
              form.reset({});
            },
            onError: () => {
              notifications.displayNotification('error', {
                headline: 'Variable could not be saved',
              });
              form.reset({});
            },
          };

          if (initialValues.name === '') {
            const result = await variablesStore.addVariable(params);
            if (result === 'VALIDATION_ERROR') {
              return {name: 'Name should be unique'};
            }
          } else if (initialValues.name === name) {
            variablesStore.updateVariable(params);
            form.reset({});
          }
        }}
      >
        {({form, handleSubmit, values}) => {
          return (
            <Form onSubmit={handleSubmit}>
              {isModificationModeEnabled && !variablesStore.hasNoContent && (
                <AddVariableButton
                  onClick={() => {
                    form.mutators.push?.('newVariables', {
                      id: generateUniqueID(),
                    });
                  }}
                  disabled={
                    form.getState().submitting ||
                    form.getState().hasValidationErrors ||
                    form.getState().validating ||
                    hasEmptyNewVariable(values)
                  }
                />
              )}
              <VariablesContainer>
                <Variables />
              </VariablesContainer>
            </Form>
          );
        }}
      </ReactFinalForm>
    </Content>
  );
});

export {VariablesContent};

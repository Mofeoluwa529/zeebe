/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {StructuredList, VariableName, VariableValue} from './styled';
import {StructuredRows} from 'modules/components/Carbon/StructuredList';
import {OnLastVariableModificationRemoved} from './OnLastVariableModificationRemoved';
import {FieldArray} from 'react-final-form-arrays';
import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {useMemo} from 'react';
import {Restricted} from 'modules/components/Restricted';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Button, Loading} from '@carbon/react';
import {useNotifications} from 'modules/notifications';
import {useForm, useFormState} from 'react-final-form';
import {Operations} from './Operations';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {Edit} from '@carbon/react/icons';
import {VariableFormValues} from 'modules/types/variables';
import {EditButtons} from './EditButtons';
import {ExistingVariableValue} from './ExistingVariableValue';
import {Name} from './NewVariableModification/Name';
import {Value} from './NewVariableModification/Value';
import {Operation} from './NewVariableModification/Operation';
import {ViewFullVariableButton} from './ViewFullVariableButton';

type Props = {
  scopeId: string | null;
  isVariableModificationAllowed?: boolean;
};

const VariablesTable: React.FC<Props> = observer(
  ({scopeId, isVariableModificationAllowed}) => {
    const {
      state: {items, loadingItemId},
    } = variablesStore;
    const {isModificationModeEnabled} = modificationsStore;

    const addVariableModifications = useMemo(
      () => modificationsStore.getAddVariableModifications(scopeId),
      [scopeId]
    );

    const {processInstanceId = ''} = useProcessInstancePageParams();
    const notifications = useNotifications();
    const {initialValues} = useFormState();

    function fetchFullVariable({
      processInstanceId,
      variableId,
      enableLoading = true,
    }: {
      processInstanceId: ProcessInstanceEntity['id'];
      variableId: VariableEntity['id'];
      enableLoading?: boolean;
    }) {
      return variablesStore.fetchVariable({
        processInstanceId,
        variableId,
        onError: () => {
          notifications.displayNotification('error', {
            headline: 'Variable could not be fetched',
          });
        },
        enableLoading,
      });
    }

    const isEditMode = (variableName: string) =>
      (initialValues?.name === variableName &&
        processInstanceDetailsStore.isRunning) ||
      (isModificationModeEnabled && isVariableModificationAllowed);

    const form = useForm<VariableFormValues>();

    return (
      <StructuredList
        dataTestId="variables-list"
        headerColumns={[
          {cellContent: 'Name', width: '35%'},
          {cellContent: 'Value', width: '55%'},
          {cellContent: '', width: '10%'},
        ]}
        headerSize="sm"
        verticalCellPadding="var(--cds-spacing-02)"
        label="Variable List"
        dynamicRows={
          isModificationModeEnabled ? (
            <>
              <OnLastVariableModificationRemoved />
              <FieldArray
                name="newVariables"
                initialValue={
                  addVariableModifications.length > 0
                    ? addVariableModifications
                    : undefined
                }
              >
                {({fields}) => (
                  <StructuredRows
                    verticalCellPadding="var(--cds-spacing-02)"
                    rows={fields
                      .map((variableName, index) => {
                        return {
                          columns: [
                            {
                              cellContent: (
                                <Name
                                  variableName={variableName}
                                  scopeId={scopeId}
                                />
                              ),
                              width: '35%',
                            },
                            {
                              cellContent: (
                                <Value
                                  variableName={variableName}
                                  scopeId={scopeId}
                                />
                              ),
                              width: '55%',
                            },
                            {
                              cellContent: (
                                <Operation
                                  variableName={variableName}
                                  onRemove={() => {
                                    fields.remove(index);
                                  }}
                                />
                              ),
                              width: '10%',
                            },
                          ],
                        };
                      })
                      .reverse()}
                  />
                )}
              </FieldArray>
            </>
          ) : undefined
        }
        rows={items.map(
          ({
            name: variableName,
            value: variableValue,
            hasActiveOperation,
            isPreview,
            id,
          }) => ({
            dataTestId: `variable-${variableName}`,
            columns: [
              {
                cellContent: <VariableName>{variableName}</VariableName>,
                width: '35%',
              },
              {
                cellContent: isEditMode(variableName) ? (
                  <ExistingVariableValue
                    id={id}
                    variableName={variableName}
                    variableValue={
                      variablesStore.getFullVariableValue(id) ?? variableValue
                    }
                    pauseValidation={
                      isPreview &&
                      variablesStore.getFullVariableValue(id) === undefined
                    }
                    onFocus={() => {
                      if (
                        isPreview &&
                        variablesStore.getFullVariableValue(id) === undefined
                      ) {
                        variablesStore.fetchVariable({
                          processInstanceId,
                          variableId: id,
                          onSuccess: (variable: VariableEntity) => {
                            variablesStore.setFullVariableValue(
                              id,
                              variable.value
                            );
                          },
                          onError: () => {
                            notifications.displayNotification('error', {
                              headline: 'Variable could not be fetched',
                            });
                          },
                        });
                      }
                    }}
                  />
                ) : (
                  <VariableValue hasBackdrop={true}>
                    {loadingItemId === id && <Loading small />}
                    {variableValue}
                  </VariableValue>
                ),
                width: '55%',
              },
              {
                cellContent: (
                  <Operations
                    showLoadingIndicator={
                      initialValues?.name !== variableName &&
                      !isModificationModeEnabled &&
                      hasActiveOperation
                    }
                  >
                    {(() => {
                      if (isModificationModeEnabled) {
                        return null;
                      }

                      if (!processInstanceDetailsStore.isRunning) {
                        if (isPreview) {
                          return (
                            <ViewFullVariableButton
                              variableName={variableName}
                              onClick={async () => {
                                const variable = await fetchFullVariable({
                                  processInstanceId,
                                  variableId: id,
                                  enableLoading: false,
                                });

                                return variable?.value ?? null;
                              }}
                            />
                          );
                        }

                        return null;
                      }

                      if (initialValues?.name === variableName) {
                        return (
                          <EditButtons
                            onExitEditMode={() =>
                              variablesStore.deleteFullVariableValue(id)
                            }
                          />
                        );
                      }

                      if (!hasActiveOperation) {
                        return (
                          <Restricted
                            scopes={['write']}
                            resourceBasedRestrictions={{
                              scopes: ['UPDATE_PROCESS_INSTANCE'],
                              permissions:
                                processInstanceDetailsStore.getPermissions(),
                            }}
                            fallback={
                              isPreview ? (
                                <ViewFullVariableButton
                                  variableName={variableName}
                                  onClick={async () => {
                                    const variable = await fetchFullVariable({
                                      processInstanceId,
                                      variableId: id,
                                      enableLoading: false,
                                    });

                                    return variable?.value ?? null;
                                  }}
                                />
                              ) : null
                            }
                          >
                            <Button
                              kind="ghost"
                              size="sm"
                              iconDescription={`Edit variable ${variableName}`}
                              data-testid="edit-variable-button"
                              disabled={
                                variablesStore.state.loadingItemId !== null
                              }
                              onClick={async () => {
                                let value = variableValue;
                                if (isPreview) {
                                  const variable = await fetchFullVariable({
                                    processInstanceId,
                                    variableId: id,
                                  });

                                  if (variable === null) {
                                    return;
                                  }

                                  variablesStore.setFullVariableValue(
                                    id,
                                    variable.value
                                  );

                                  value = variable.value;
                                }

                                form.reset({
                                  name: variableName,
                                  value,
                                });
                                form.change('value', value);
                              }}
                              hasIconOnly
                              tooltipPosition="left"
                              renderIcon={Edit}
                            />
                          </Restricted>
                        );
                      }
                    })()}
                  </Operations>
                ),
                width: '10%',
              },
            ],
          })
        )}
      />
    );
  }
);

export {VariablesTable};

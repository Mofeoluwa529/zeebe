/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as Styled from './styled';
import {getSortParams} from 'modules/utils/filter';
import {useNavigate, useLocation} from 'react-router-dom';
import {tracking} from 'modules/tracking';

function toggleSorting(
  search: string,
  column: string,
  table: 'instances' | 'instance'
) {
  const params = new URLSearchParams(search);
  const {sortBy, sortOrder} = getSortParams(search) || {
    sortBy: table === 'instances' ? 'startDate' : 'errorType',
    sortOrder: 'desc',
  };

  if (params.get('sort') === null) {
    params.set('sort', `${column}+desc`);
  }

  if (sortBy === column) {
    if (sortOrder === 'asc') {
      params.set('sort', `${column}+desc`);
    } else {
      params.set('sort', `${column}+asc`);
    }
  } else {
    params.set('sort', `${column}+desc`);
  }

  return params.toString();
}

type Props = {
  disabled?: boolean;
  label: string;
  sortKey?: string;
  table?: 'instances' | 'instance';
};

function getSortOrder({
  disabled,
  sortKey,
  sortBy,
  sortOrder,
}: Pick<Props, 'disabled' | 'sortKey'> & {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}) {
  if (disabled) {
    return undefined;
  }

  return sortKey === sortBy ? sortOrder : undefined;
}

const ColumnHeader: React.FC<Props> = ({
  sortKey,
  disabled,
  label,
  table = 'instances',
}) => {
  const isSortable = sortKey !== undefined;
  const navigate = useNavigate();
  const location = useLocation();

  const {sortBy, sortOrder} = getSortParams(location.search) || {
    sortBy: table === 'instances' ? 'startDate' : 'errorType',
    sortOrder: 'desc',
  };
  const isActive = isSortable && sortKey === sortBy;

  if (isSortable) {
    return (
      <Styled.SortColumnHeader
        disabled={disabled}
        onClick={() => {
          if (!disabled && sortKey !== undefined) {
            tracking.track({
              eventName: 'incidents-sorted',
              column: sortKey,
            });

            navigate({
              search: toggleSorting(location.search, sortKey, table),
            });
          }
        }}
        title={`Sort by ${sortKey}`}
        data-testid={`sort-by-${sortKey}`}
      >
        <Styled.Label active={isActive} disabled={disabled}>
          {label}
        </Styled.Label>
        <Styled.SortIcon
          active={isActive}
          disabled={disabled}
          sortOrder={getSortOrder({
            sortKey,
            disabled,
            sortBy,
            sortOrder,
          })}
        />
      </Styled.SortColumnHeader>
    );
  }

  return (
    <Styled.ColumnHeader>
      <Styled.Label active={isActive} disabled={disabled}>
        {label}
      </Styled.Label>
    </Styled.ColumnHeader>
  );
};

export {ColumnHeader};

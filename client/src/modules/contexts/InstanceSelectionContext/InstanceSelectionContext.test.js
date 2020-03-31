/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useContext, useEffect} from 'react';
import {mount} from 'enzyme';
import {renderHook, act} from '@testing-library/react-hooks';

import InstanceSelectionContext, {
  InstanceSelectionProvider,
  useInstanceSelection,
} from './InstanceSelectionContext';

const INSTANCE_IDS = {
  A: '4503599627371065',
  B: '2737113845035996',
};

const TOTAL_COUNT = Object.values(INSTANCE_IDS).length;

describe('InstanceSelectionContext', () => {
  it('should return false when there is no selection', () => {
    // when
    const {result} = renderHook(() => useInstanceSelection(2));

    // then
    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(false);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(false);
    expect(result.current.isAllChecked).toBe(false);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(0);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should add id to selection and return true', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));

    // when
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });

    // then
    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(false);
    expect(result.current.isAllChecked).toBe(false);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(1);
    expect(result.current.ids).toEqual([INSTANCE_IDS.A]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should add two ids and check all', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));

    // when
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });

    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.B)();
    });

    // then
    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(true);
    expect(result.current.isAllChecked).toBe(true);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(2);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should add and remove selection and return false', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));

    // when
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });

    // then
    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(false);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(false);
    expect(result.current.isAllChecked).toBe(false);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(0);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should select all', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));

    // when
    act(() => {
      result.current.handleCheckAll();
    });

    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(true);
    expect(result.current.isAllChecked).toBe(true);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(2);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should select all, unselect one', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));

    // when
    act(() => {
      result.current.handleCheckAll();
    });

    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });

    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(false);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(true);
    expect(result.current.isAllChecked).toBe(false);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(1);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([INSTANCE_IDS.A]);
  });

  it('should check all when checking single instances', () => {
    // given
    const {result} = renderHook(() => useInstanceSelection(2));
    act(() => {
      result.current.handleCheckAll();
    });

    // when
    // uncheck one
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });
    // check one again
    act(() => {
      result.current.handleCheckInstance(INSTANCE_IDS.A)();
    });

    expect(result.current.isInstanceChecked(INSTANCE_IDS.A)).toBe(true);
    expect(result.current.isInstanceChecked(INSTANCE_IDS.B)).toBe(true);
    expect(result.current.isAllChecked).toBe(true);
    expect(result.current.getSelectedCount(TOTAL_COUNT)).toBe(2);
    expect(result.current.ids).toEqual([]);
    expect(result.current.excludeIds).toEqual([]);
  });

  it('should be consumable with useContext', () => {
    // given
    const Component = () => {
      const {isInstanceChecked, handleCheckInstance} = useContext(
        InstanceSelectionContext
      );

      useEffect(() => {
        handleCheckInstance(INSTANCE_IDS.A)();
      }, []);

      return (
        <>{isInstanceChecked(INSTANCE_IDS.A) ? 'selected' : 'not selected'}</>
      );
    };

    // when
    const node = mount(<Component />, {
      wrappingComponent: InstanceSelectionProvider,
    });

    // then
    expect(node.html()).toEqual('selected');
  });
});

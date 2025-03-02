/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ColumnType } from './ColumnType'

/**
 * A SQL field.
 *
 * Matches the Calcite JSON format.
 */
export type Field = {
  columntype: ColumnType
  name: string
}

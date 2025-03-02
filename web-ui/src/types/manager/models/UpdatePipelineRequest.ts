/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { AttachedConnector } from './AttachedConnector'
import type { ProgramId } from './ProgramId'
import type { RuntimeConfig } from './RuntimeConfig'

/**
 * Request to update an existing pipeline.
 */
export type UpdatePipelineRequest = {
  config?: RuntimeConfig | null
  /**
   * Attached connectors.
   *
   * - If absent, existing connectors will be kept unmodified.
   *
   * - If present all existing connectors will be replaced with the new
   * specified list.
   */
  connectors?: Array<AttachedConnector> | null
  /**
   * New pipeline description.
   */
  description: string
  /**
   * New pipeline name.
   */
  name: string
  program_id?: ProgramId | null
}

// Display a table with all SQL programs.
//
// Can edit name and description directly in the table.
// Also display status of the program.

import { useRouter } from 'next/router'
import { useState, useCallback } from 'react'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import Typography from '@mui/material/Typography'
import { GridColDef, GridRenderCellParams, useGridApiRef } from '@mui/x-data-grid-pro'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { match, P } from 'ts-pattern'

import CustomChip from 'src/@core/components/mui/chip'
import { ProgramsService } from 'src/types/manager/services/ProgramsService'
import { ProgramDescr } from 'src/types/manager/models/ProgramDescr'
import { ProgramStatus } from 'src/types/manager/models/ProgramStatus'
import { CancelError, ProgramId, UpdateProgramRequest, UpdateProgramResponse } from 'src/types/manager'
import EntityTable from 'src/components/table/EntityTable'
import useStatusNotification from 'src/components/errors/useStatusNotification'

const getStatusObj = (status: ProgramStatus) =>
  match(status)
    .with({ SqlError: P._ }, () => {
      return { title: 'SQL Error', color: 'error' as const }
    })
    .with({ RustError: P._ }, () => {
      return { title: 'Rust Error', color: 'error' as const }
    })
    .with({ SystemError: P._ }, () => {
      return { title: 'System Error', color: 'error' as const }
    })
    .with('Pending', () => {
      return { title: 'Compiling', color: 'primary' as const }
    })
    .with('CompilingSql', () => {
      return { title: 'Compiling', color: 'primary' as const }
    })
    .with('CompilingRust', () => {
      return { title: 'Building Pipeline', color: 'primary' as const }
    })
    .with('Success', () => {
      return { title: 'Ready', color: 'success' as const }
    })
    .with('None', () => {
      return { title: 'Unused', color: 'primary' as const }
    })
    .exhaustive()

const TableSqlPrograms = () => {
  const router = useRouter()

  const [rows, setRows] = useState<ProgramDescr[]>([])
  const fetchQuery = useQuery<ProgramDescr[]>({ queryKey: ['program'] })
  const { pushMessage } = useStatusNotification()

  const apiRef = useGridApiRef()
  const queryClient = useQueryClient()

  // Table columns
  const columns: GridColDef[] = [
    {
      flex: 0.05,
      minWidth: 50,
      field: 'program_id',
      headerName: 'ID',
      renderCell: (params: GridRenderCellParams) => {
        const { row } = params

        return (
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
              <Typography noWrap variant='body2' sx={{ color: 'text.primary', fontWeight: 600 }}>
                {row.program_id}
              </Typography>
            </Box>
          </Box>
        )
      }
    },
    {
      flex: 0.3,
      minWidth: 290,
      headerName: 'Name',
      field: 'name',
      editable: true
    },
    {
      flex: 0.5,
      minWidth: 110,
      field: 'description',
      headerName: 'Description',
      renderCell: (params: GridRenderCellParams) => (
        <Typography variant='body2' sx={{ color: 'text.primary' }}>
          {params.row.description}
        </Typography>
      ),
      editable: true
    },
    {
      flex: 0.15,
      minWidth: 140,
      field: 'status',
      headerName: 'Status',
      renderCell: (params: GridRenderCellParams) => {
        const status = getStatusObj(params.row.status)

        return <CustomChip rounded size='small' skin='light' color={status.color} label={status.title} />
      }
    }
  ]

  // Editing a row
  const mutation = useMutation<
    UpdateProgramResponse,
    CancelError,
    { program_id: ProgramId; request: UpdateProgramRequest }
  >(args => {
    return ProgramsService.updateProgram(args.program_id, args.request)
  })
  const processRowUpdate = (newRow: ProgramDescr, oldRow: ProgramDescr) => {
    mutation.mutate(
      {
        program_id: newRow.program_id,
        request: { description: newRow.description, name: newRow.name }
      },
      {
        onError: (error: CancelError) => {
          queryClient.invalidateQueries(['program'])
          queryClient.invalidateQueries(['programStatus', { program_id: newRow.program_id }])
          pushMessage({ message: error.message, key: new Date().getTime(), color: 'error' })
          apiRef.current.updateRows([oldRow])
        }
      }
    )

    return newRow
  }

  // Deleting a row
  const deleteMutation = useMutation<void, CancelError, string>(ProgramsService.deleteProgram)
  const deleteProject = useCallback(
    (curRow: ProgramDescr) => {
      setTimeout(() => {
        const oldRow = rows.find(row => row.program_id === curRow.program_id)
        if (oldRow !== undefined) {
          deleteMutation.mutate(curRow.program_id, {
            onSuccess: () => {
              setRows(prevRows => prevRows.filter(row => row.program_id !== curRow.program_id))
            },
            onError: error => {
              pushMessage({ message: error.message, key: new Date().getTime(), color: 'error' })
              queryClient.invalidateQueries(['program'])
            }
          })
        }
      })
    },
    [queryClient, deleteMutation, rows, pushMessage]
  )

  // Table properties, passed to the underlying grid-table
  const tableProps = {
    getRowId: (row: ProgramDescr) => row.program_id,
    columnVisibilityModel: { program_id: false },
    columns: columns,
    rows: rows
  }

  return (
    <Card>
      <EntityTable
        hasSearch
        hasFilter
        addActions
        tableProps={tableProps}
        setRows={setRows}
        fetchRows={fetchQuery}
        onUpdateRow={processRowUpdate}
        onDeleteRow={deleteProject}
        onEditClicked={row => router.push('/analytics/editor/' + row.program_id)}
        apiRef={apiRef}
      />
    </Card>
  )
}

export default TableSqlPrograms

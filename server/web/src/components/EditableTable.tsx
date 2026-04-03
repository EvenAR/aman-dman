import type { ReactNode } from 'react';

export interface EditableColumn<T> {
  key: Extract<keyof T, string>;
  label: string;
  type?: 'text' | 'number' | 'select';
  options?: string[];
  placeholder?: string;
  width?: string;
}

interface EditableTableProps<T extends object> {
  rows: T[];
  columns: EditableColumn<T>[];
  onChange: (rows: T[]) => void;
  createRow: () => T;
  title: string;
}

function renderInput<T extends object>(
  row: T,
  rowIndex: number,
  column: EditableColumn<T>,
  onChange: (nextRows: T[]) => void,
  rows: T[]
): ReactNode {
  const value = (row as Record<string, unknown>)[column.key];

  const update = (nextValue: unknown): void => {
    const nextRows = rows.map((entry, index) =>
      index === rowIndex ? ({ ...entry, [column.key]: nextValue } as T) : entry
    );
    onChange(nextRows);
  };

  if (column.type === 'select') {
    return (
      <select value={String(value ?? '')} onChange={(event) => update(event.target.value)}>
        <option value="">Select</option>
        {(column.options ?? []).map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    );
  }

  if (column.type === 'number') {
    return (
      <input
        type="number"
        value={value === null || value === undefined ? '' : String(value)}
        placeholder={column.placeholder}
        onChange={(event) => update(event.target.value === '' ? null : Number(event.target.value))}
      />
    );
  }

  return (
    <input
      type="text"
      value={value === null || value === undefined ? '' : String(value)}
      placeholder={column.placeholder}
      onChange={(event) => update(event.target.value)}
    />
  );
}

export function EditableTable<T extends object>({
  rows,
  columns,
  onChange,
  createRow,
  title,
}: EditableTableProps<T>): React.JSX.Element {
  return (
    <section className="editor-table">
      <header className="editor-table__header">
        <h3>{title}</h3>
        <button
          type="button"
          className="ghost-button"
          onClick={() => onChange([...rows, createRow()])}
        >
          Add row
        </button>
      </header>
      <div className="editor-table__scroll">
        <table>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={String(column.key)} style={{ width: column.width }}>
                  {column.label}
                </th>
              ))}
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length + 1} className="empty-state">
                  No rows yet.
                </td>
              </tr>
            ) : (
              rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {columns.map((column) => (
                    <td key={String(column.key)}>
                      {renderInput(row, rowIndex, column, onChange, rows)}
                    </td>
                  ))}
                  <td className="row-actions">
                    <button
                      type="button"
                      className="danger-link"
                      onClick={() => onChange(rows.filter((_, index) => index !== rowIndex))}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

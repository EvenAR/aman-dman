'use client';

import { startTransition, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';

import { cloneValue, isEqual, useBeforeUnload } from '../lib/editor-state';

interface EntityEditorPageProps<T> {
  title: string;
  description: string;
  records: T[];
  createEmpty: () => T;
  getKey: (record: T) => string;
  getLabel: (record: T) => string;
  renderEditor: (draft: T, onChange: (value: T) => void) => React.ReactNode;
  onSave: (draft: T, previousDraft: T) => Promise<T>;
  onDelete?: (draft: T) => Promise<void>;
  cloneDraft?: (draft: T) => T;
  onAfterSave?: (saved: T, previousDraft: T) => Promise<void> | void;
  onAfterDelete?: (deletedDraft: T) => Promise<void> | void;
  validate?: (draft: T) => string | null;
  allowCreate?: boolean;
  showRecordList?: boolean;
  emptyListMessage?: string;
}

function resolveInitialSelection<T>(
  records: T[],
  createEmpty: () => T,
  getKey: (record: T) => string,
  preferredKey: string | null
): { selectedKey: string; draft: T; originalDraft: T } {
  const selectedRecord =
    (preferredKey ? records.find((record) => getKey(record) === preferredKey) : null) ??
    records[0] ??
    null;

  if (!selectedRecord) {
    const emptyRecord = createEmpty();
    return {
      selectedKey: 'new',
      draft: emptyRecord,
      originalDraft: cloneValue(emptyRecord),
    };
  }

  return {
    selectedKey: getKey(selectedRecord),
    draft: cloneValue(selectedRecord),
    originalDraft: cloneValue(selectedRecord),
  };
}

export function EntityEditorPage<T>({
  title,
  description,
  records,
  createEmpty,
  getKey,
  getLabel,
  renderEditor,
  onSave,
  onDelete,
  cloneDraft,
  onAfterSave,
  onAfterDelete,
  validate,
  allowCreate = true,
  showRecordList = true,
  emptyListMessage = 'No records in this section yet.',
}: EntityEditorPageProps<T>): React.JSX.Element {
  const router = useRouter();
  const [recordsState, setRecordsState] = useState<T[]>(records);
  const initialSelection = resolveInitialSelection(records, createEmpty, getKey, null);
  const [selectedKey, setSelectedKey] = useState<string>(initialSelection.selectedKey);
  const [draft, setDraft] = useState<T>(initialSelection.draft);
  const [originalDraft, setOriginalDraft] = useState<T>(initialSelection.originalDraft);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const selectedKeyRef = useRef(selectedKey);

  const dirty = !isEqual(draft, originalDraft);
  const validationError = validate?.(draft) ?? null;
  useBeforeUnload(dirty);

  useEffect(() => {
    selectedKeyRef.current = selectedKey;
  }, [selectedKey]);

  useEffect(() => {
    setRecordsState(records);
    const nextSelection = resolveInitialSelection(
      records,
      createEmpty,
      getKey,
      selectedKeyRef.current
    );
    setSelectedKey(nextSelection.selectedKey);
    setDraft(nextSelection.draft);
    setOriginalDraft(nextSelection.originalDraft);
  }, [createEmpty, getKey, records]);

  function confirmIfDirty(): boolean {
    return !dirty || window.confirm('Discard unsaved changes?');
  }

  function openRecord(record: T): void {
    const nextKey = getKey(record);
    setSelectedKey(nextKey);
    setDraft(cloneValue(record));
    setOriginalDraft(cloneValue(record));
    setError(null);
    setNotice(null);
  }

  function handleCreateNew(): void {
    if (!confirmIfDirty()) {
      return;
    }

    const emptyRecord = createEmpty();
    setSelectedKey('new');
    setDraft(emptyRecord);
    setOriginalDraft(cloneValue(emptyRecord));
    setError(null);
    setNotice(null);
  }

  function handleClone(): void {
    if (!cloneDraft || selectedKey === 'new') {
      return;
    }

    const clonedDraft = cloneDraft(cloneValue(draft));
    setSelectedKey('new');
    setDraft(clonedDraft);
    setOriginalDraft(cloneValue(clonedDraft));
    setError(null);
    setNotice('Draft cloned. Save to create a new record.');
  }

  async function handleSave(): Promise<void> {
    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const previousDraft = cloneValue(draft);
      const saved = await onSave(draft, previousDraft);
      const previousKey = selectedKey === 'new' ? null : selectedKey;
      const savedKey = getKey(saved);
      const nextRecords =
        previousKey === null
          ? [...recordsState, saved]
          : recordsState.map((record) => (getKey(record) === previousKey ? saved : record));

      nextRecords.sort((left, right) => getLabel(left).localeCompare(getLabel(right)));
      setRecordsState(nextRecords);
      setSelectedKey(savedKey);
      setDraft(cloneValue(saved));
      setOriginalDraft(cloneValue(saved));
      setNotice(`${title} saved.`);
      await onAfterSave?.(saved, previousDraft);
      startTransition(() => router.refresh());
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(): Promise<void> {
    if (!onDelete || selectedKey === 'new') {
      return;
    }

    if (!window.confirm('Delete the selected record?')) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const deletedDraft = cloneValue(draft);
      await onDelete(draft);
      const nextRecords = recordsState.filter((record) => getKey(record) !== selectedKey);
      setRecordsState(nextRecords);
      const nextSelection = resolveInitialSelection(nextRecords, createEmpty, getKey, null);
      setSelectedKey(nextSelection.selectedKey);
      setDraft(nextSelection.draft);
      setOriginalDraft(nextSelection.originalDraft);
      setNotice(`${title} deleted.`);
      await onAfterDelete?.(deletedDraft);
      startTransition(() => router.refresh());
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="route-page">
      <header className="workspace__header">
        <div>
          <span className="eyebrow">Editor</span>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
        <div className="workspace__actions">
          {allowCreate ? (
            <button type="button" className="ghost-button" onClick={handleCreateNew}>
              New
            </button>
          ) : null}
          {cloneDraft ? (
            <button
              type="button"
              className="ghost-button"
              onClick={handleClone}
              disabled={saving || selectedKey === 'new'}
            >
              Clone
            </button>
          ) : null}
          <button
            type="button"
            className="primary-button"
            onClick={() => void handleSave()}
            disabled={saving || validationError !== null}
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
          {onDelete ? (
            <button
              type="button"
              className="danger-button"
              onClick={() => void handleDelete()}
              disabled={saving || selectedKey === 'new'}
            >
              Delete
            </button>
          ) : null}
        </div>
      </header>

      {error ? <div className="banner banner--error">{error}</div> : null}
      {!error && validationError ? (
        <div className="banner banner--error">{validationError}</div>
      ) : null}
      {notice ? <div className="banner banner--success">{notice}</div> : null}

      <div
        className={
          showRecordList ? 'workspace__content' : 'workspace__content workspace__content--single'
        }
      >
        {showRecordList ? (
          <section className="list-panel">
            <header className="panel-header">
              <h3>Records</h3>
              <span>{recordsState.length} items</span>
            </header>
            <div className="list-panel__items">
              {recordsState.length === 0 ? (
                <div className="empty-state">{emptyListMessage}</div>
              ) : (
                recordsState.map((record) => {
                  const recordKey = getKey(record);
                  return (
                    <button
                      key={recordKey}
                      type="button"
                      className={
                        recordKey === selectedKey ? 'list-item list-item--active' : 'list-item'
                      }
                      onClick={() => {
                        if (!confirmIfDirty()) {
                          return;
                        }
                        openRecord(record);
                      }}
                    >
                      {getLabel(record)}
                    </button>
                  );
                })
              )}
            </div>
          </section>
        ) : null}

        <section className="editor-panel">
          <div className="editor-stack">{renderEditor(draft, setDraft)}</div>
        </section>
      </div>
    </div>
  );
}

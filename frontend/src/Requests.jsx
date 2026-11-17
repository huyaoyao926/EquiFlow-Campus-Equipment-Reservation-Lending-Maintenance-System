import { useEffect, useState } from 'react';
import { api, day, moment, query, statuses } from './api';
import {
  Badge,
  Dialog,
  Empty,
  EquipmentArt,
  Field,
  FormError,
  Icon,
  Loading,
  Pagination,
} from './components';

const actionLabels = {
  approve: 'Approve request',
  reject: 'Decline request',
  cancel: 'Cancel reservation',
  checkout: 'Record pickup',
  return: 'Record return',
};

function RequestDetail({ initial, user, config, onClose, onChange, notify }) {
  const [record, setRecord] = useState(initial);
  const [action, setAction] = useState('');
  const [note, setNote] = useState('');
  const [damaged, setDamaged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const admin = user.role === 'ADMIN';
  const r = record;
  const requiresNote =
    action === 'reject' || (action === 'cancel' && admin) || (action === 'return' && damaged);
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const updated = await api(`/api/reservations/${r.id}/${action}`, {
        method: 'POST',
        body: { version: r.version, note, damaged },
      });
      setRecord(updated);
      setAction('');
      setNote('');
      onChange();
      notify(`${updated.reference} updated: ${statuses[updated.status].toLowerCase()}.`);
    } catch (failure) {
      setError(failure.message);
      if (failure.status === 409) {
        try {
          setRecord(await api(`/api/reservations/${r.id}`));
          setAction('');
          setNote('');
        } catch {}
      }
    } finally {
      setBusy(false);
    }
  }
  const normalFlow = ['PENDING', 'APPROVED', 'CHECKED_OUT', 'RETURNED'];
  const flowIndex = normalFlow.indexOf(r.status);
  return (
    <Dialog
      title={r.reference}
      subtitle={`Submitted ${moment(r.createdAt)} / Singapore time`}
      onClose={onClose}
      busy={busy}
      wide
    >
      <div className="request-detail-body">
        <div className="request-equipment">
          <EquipmentArt category={r.category} compact />
          <div>
            <h3>{r.equipmentName}</h3>
            <p>
              {r.assetTag} / {r.location}
            </p>
          </div>
          <Badge status={r.status} overdue={r.overdue} />
        </div>
        {flowIndex >= 0 && (
          <ol className="request-progress">
            {normalFlow.map((status, index) => (
              <li key={status} className={index <= flowIndex ? 'complete' : ''}>
                <span>{index < flowIndex ? <Icon name="check" size={14} /> : index + 1}</span>
                {statuses[status]}
              </li>
            ))}
          </ol>
        )}
        <div className="request-info-grid">
          <div>
            <span className="detail-label">RESERVATION PERIOD</span>
            <strong>
              {day(r.startDate)} - {day(r.endDate)}
            </strong>
          </div>
          <div>
            <span className="detail-label">REQUESTER</span>
            <strong>{r.requesterName}</strong>
            <small>{r.requesterEmail}</small>
          </div>
          <div>
            <span className="detail-label">PROJECT / PURPOSE</span>
            <p className="preserve-lines">{r.purpose}</p>
          </div>
          <div>
            <span className="detail-label">COLLECTION & RETURN</span>
            <p>
              {r.checkedOutAt
                ? `Collected ${moment(r.checkedOutAt)}`
                : `Collect from ${r.location} during the approved dates.`}
            </p>
            {r.returnedAt && <p>Returned {moment(r.returnedAt)}</p>}
          </div>
        </div>
        {r.reviewNote && (
          <div className="review-note">
            <Icon name="info" size={18} />
            <div>
              <strong>Desk / request note</strong>
              <p className="preserve-lines">{r.reviewNote}</p>
              {r.reviewedBy && (
                <small>
                  Reviewed by {r.reviewedBy}
                  {r.reviewedAt ? ` / ${moment(r.reviewedAt)}` : ''}
                </small>
              )}
            </div>
          </div>
        )}
        {r.overdue && (
          <div className="form-error">
            <Icon name="clock" size={18} />
            <span>This item is overdue. Return it to {r.location} as soon as possible.</span>
          </div>
        )}
        <FormError error={error} />
        {action ? (
          <form className="action-form" onSubmit={submit}>
            <h3>{actionLabels[action]}</h3>
            <p>
              {action === 'approve'
                ? 'Approval holds this asset for the requested dates. If those dates are already committed, this approval will be blocked.'
                : action === 'checkout'
                  ? 'Confirm the requester has collected the equipment and all included items.'
                  : action === 'return'
                    ? 'Confirm the equipment and its included items have been returned to the desk.'
                    : 'The requester will see this decision and any note you add.'}
            </p>
            {action === 'return' && (
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={damaged}
                  onChange={(e) => setDamaged(e.target.checked)}
                />
                Returned with damage - open a maintenance job
              </label>
            )}
            {config.demoEnabled && (
              <button
                type="button"
                className="button secondary demo-fill"
                onClick={() =>
                  setNote(
                    action === 'return' && damaged
                      ? 'Camera battery compartment damaged. Inspect and repair before the next loan.'
                      : {
                          approve:
                            'Student project verified. All included items are ready for collection.',
                          reject:
                            'Required equipment is reserved for scheduled laboratory teaching.',
                          cancel:
                            'Project schedule changed. Please release these reservation dates.',
                          checkout:
                            'Student card checked. Camera, lens, charger and carry bag collected.',
                          return: 'All included items returned, inspected and accounted for.',
                        }[action],
                  )
                }
              >
                Fill example note
              </button>
            )}
            <Field label={requiresNote ? 'Reason (required)' : 'Note (optional)'}>
              <textarea
                autoFocus
                rows={3}
                maxLength={500}
                required={requiresNote}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder={
                  requiresNote
                    ? 'Explain the reason for this decision.'
                    : 'Add a useful detail for the record.'
                }
              />
            </Field>
            <div className="dialog-actions">
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={() => {
                  setAction('');
                  setError('');
                }}
              >
                Back
              </button>
              <button
                className={`button ${['reject', 'cancel'].includes(action) ? 'danger' : 'primary'}`}
                disabled={busy}
              >
                {busy ? 'Saving...' : actionLabels[action]}
              </button>
            </div>
          </form>
        ) : (
          <div className="request-actions">
            <a
              className="button secondary"
              href={`/api/reservations/${r.id}/receipt`}
              target="_blank"
              rel="noreferrer"
            >
              Print loan receipt
            </a>
            {admin && r.status === 'PENDING' && (
              <>
                <button className="button primary" onClick={() => setAction('approve')}>
                  <Icon name="check" size={17} />
                  Approve request
                </button>
                <button className="button secondary" onClick={() => setAction('reject')}>
                  Decline
                </button>
              </>
            )}
            {admin && r.status === 'APPROVED' && (
              <button
                className="button primary"
                disabled={r.startDate > config.today || r.endDate < config.today}
                onClick={() => setAction('checkout')}
              >
                <Icon name="box" size={17} />
                {r.startDate > config.today ? `Pickup opens ${day(r.startDate)}` : 'Record pickup'}
              </button>
            )}
            {admin && r.status === 'CHECKED_OUT' && (
              <button className="button primary" onClick={() => setAction('return')}>
                <Icon name="check" size={17} />
                Record return
              </button>
            )}
            {['PENDING', 'APPROVED'].includes(r.status) && (
              <button className="button subtle-danger" onClick={() => setAction('cancel')}>
                Cancel reservation
              </button>
            )}
            <button className="button secondary push-right" onClick={onClose}>
              Close
            </button>
          </div>
        )}
      </div>
    </Dialog>
  );
}

export default function Requests({
  user,
  config,
  refresh,
  onChange,
  notify,
  focusId,
  initialStatus,
  go,
}) {
  const [status, setStatus] = useState(initialStatus);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);
  const admin = user.role === 'ADMIN';
  useEffect(() => {
    setStatus(initialStatus);
    setPage(0);
  }, [initialStatus]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const timer = setTimeout(
      () =>
        api(
          `/api/reservations?${query({ status: status === 'OVERDUE' ? '' : status, overdue: status === 'OVERDUE', search, page })}`,
        )
          .then((result) => {
            if (active) setData(result);
          })
          .catch((failure) => {
            if (active) setError(failure.message);
          })
          .finally(() => {
            if (active) setLoading(false);
          }),
      180,
    );
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [status, search, page, refresh]);
  useEffect(() => {
    let active = true;
    if (focusId)
      api(`/api/reservations/${focusId}`)
        .then((result) => {
          if (active) setSelected(result);
        })
        .catch((failure) => {
          if (active) notify(failure.message, 'error');
        });
    return () => {
      active = false;
    };
  }, [focusId, notify]);
  async function open(id) {
    try {
      setSelected(await api(`/api/reservations/${id}`));
    } catch (failure) {
      notify(failure.message, 'error');
    }
  }
  async function download() {
    setExporting(true);
    try {
      const response = await fetch(
        `/api/reservations/export?${query({ status: status === 'OVERDUE' ? '' : status, overdue: status === 'OVERDUE', search })}`,
        { credentials: 'same-origin', headers: { 'Accept-Language': 'en' } },
      );
      if (!response.ok)
        throw new Error(
          'Could not export request records. Please sign in again if your session has ended.',
        );
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a');
      link.href = url;
      link.download = 'EquiFlow-requests.csv';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      notify('Request records exported.');
    } catch (failure) {
      notify(failure.message, 'error');
    } finally {
      setExporting(false);
    }
  }
  const filters = [
    ['', 'All requests'],
    ['PENDING', 'Pending'],
    ['APPROVED', 'Approved'],
    ['CHECKED_OUT', 'On loan'],
    ['OVERDUE', 'Overdue'],
    ['RETURNED', 'Returned'],
  ];
  return (
    <>
      <div className="request-toolbar">
        <div className="search-field">
          <Icon name="search" size={19} />
          <input
            aria-label="Search requests"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder={
              admin ? 'Search equipment, asset tag or requester' : 'Search equipment or asset tag'
            }
          />
        </div>
        {config.demoEnabled && (
          <button
            type="button"
            className="button secondary"
            onClick={() => {
              setSearch(data?.items[0]?.assetTag || 'CAM-001');
              setStatus('');
              setPage(0);
            }}
          >
            Fill example search
          </button>
        )}
        <button className="button secondary" disabled={exporting} onClick={download}>
          <Icon name="download" size={17} />
          {exporting ? 'Exporting...' : 'Export CSV'}
        </button>
      </div>
      <div className="request-filter-row">
        <div className="category-tabs" role="group" aria-label="Request status">
          {filters.map(([key, label]) => (
            <button
              key={key}
              className={status === key ? 'selected' : ''}
              aria-pressed={status === key}
              onClick={() => {
                setStatus(key);
                setPage(0);
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <select
          aria-label="Additional request status"
          value={['REJECTED', 'CANCELLED', 'EXPIRED'].includes(status) ? status : ''}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
        >
          <option value="">Other statuses</option>
          {['REJECTED', 'CANCELLED', 'EXPIRED'].map((key) => (
            <option key={key} value={key}>
              {statuses[key]}
            </option>
          ))}
        </select>
      </div>
      <FormError error={error} />
      <section className="panel requests-panel">
        {loading ? (
          <Loading />
        ) : !data?.items.length ? (
          <Empty
            title="No requests to show"
            icon="calendar"
            action={
              !admin && !status && !search ? (
                <button className="button primary" onClick={() => go('equipment')}>
                  Browse equipment
                </button>
              ) : undefined
            }
          >
            {status || search
              ? 'Try another status or change your search.'
              : 'Reservation requests and loan records will appear here.'}
          </Empty>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Request / equipment</th>
                  {admin && <th>Requester</th>}
                  <th>Reservation dates</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <span className="cell-overline">{r.reference}</span>
                      <button className="table-name" onClick={() => open(r.id)}>
                        {r.equipmentName}
                      </button>
                      <span className="cell-secondary">{r.assetTag}</span>
                    </td>
                    {admin && (
                      <td>
                        <strong className="cell-person">{r.requesterName}</strong>
                        <span className="cell-secondary">{r.requesterEmail}</span>
                      </td>
                    )}
                    <td>
                      {day(r.startDate)}
                      <span className="cell-secondary">to {day(r.endDate)}</span>
                    </td>
                    <td>
                      <Badge status={r.status} overdue={r.overdue} />
                    </td>
                    <td>
                      <button className="button small secondary" onClick={() => open(r.id)}>
                        {admin && r.status === 'PENDING' ? 'Review' : 'View details'}
                        <Icon name="chevron" size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <Pagination data={loading ? null : data} onPage={setPage} />
      <div className="small-copy muted requests-footnote">
        {admin
          ? 'Pickup and return must be recorded by the equipment desk. Every decision is saved in the activity log.'
          : 'You can cancel pending and approved reservations. Contact the equipment desk to record pickup or return.'}
      </div>
      {selected && (
        <RequestDetail
          key={selected.id}
          initial={selected}
          user={user}
          config={config}
          onClose={() => setSelected(null)}
          onChange={onChange}
          notify={notify}
        />
      )}
    </>
  );
}

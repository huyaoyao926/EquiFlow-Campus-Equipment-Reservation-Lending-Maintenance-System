import { useEffect, useRef, useState } from 'react';
import { addDays, api, categories, day, query, statuses } from './api';
import DateInput from './DateInput';
import { dateMessage } from './englishValidation';
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

function EquipmentEditor({ item, onClose, onSaved, notify, config }) {
  const [values, setValues] = useState(
    item
      ? { ...item }
      : {
          assetTag: '',
          name: '',
          category: 'CAMERA',
          description: '',
          location: '',
          status: 'READY',
          version: null,
        },
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const change = (key, value) => setValues((previous) => ({ ...previous, [key]: value }));
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setFields({});
    const { assetTag, name, category, description, location, status, version } = values;
    try {
      await api(`/api/equipment${item ? `/${item.id}` : ''}`, {
        method: item ? 'PUT' : 'POST',
        body: { assetTag, name, category, description, location, status, version },
      });
      notify(item ? 'Equipment details saved.' : 'Equipment added to the catalogue.');
      onSaved();
      onClose();
    } catch (failure) {
      setError(failure.message);
      setFields(failure.fields || {});
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={item ? 'Edit equipment' : 'Add equipment'}
      subtitle="One catalogue record represents one physical asset."
      onClose={onClose}
      busy={busy}
    >
      <form onSubmit={submit} className="dialog-body">
        {config.demoEnabled && (
          <button
            type="button"
            className="button secondary demo-fill"
            onClick={() => {
              setValues({
                ...values,
                assetTag: item?.assetTag || `DEMO-${Date.now().toString(36).toUpperCase()}`,
                name: 'Campus presentation camera kit',
                category: 'CAMERA',
                description:
                  'Camera, lens, battery, charger and carry bag. Prepared for the campus project demonstration.',
                location: 'Media Lab, Level 2',
                status: item?.status || 'READY',
              });
              setFields({});
              setError('');
            }}
          >
            Fill example equipment
          </button>
        )}
        <FormError error={error} />
        <div className="form-grid">
          <Field label="Asset tag" error={fields.assetTag} hint="For example, CAM-004">
            <input
              value={values.assetTag}
              onChange={(event) => change('assetTag', event.target.value.toUpperCase())}
              required
              pattern="[A-Z0-9][A-Z0-9-]{2,31}"
              maxLength={32}
            />
          </Field>
          <Field label="Category" error={fields.category}>
            <select
              value={values.category}
              onChange={(event) => change('category', event.target.value)}
            >
              {Object.entries(categories).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Equipment name" error={fields.name}>
          <input
            value={values.name}
            onChange={(event) => change('name', event.target.value)}
            required
            maxLength={100}
          />
        </Field>
        <Field label="Description and included items" error={fields.description}>
          <textarea
            value={values.description}
            onChange={(event) => change('description', event.target.value)}
            required
            maxLength={1000}
            rows={4}
          />
        </Field>
        <Field label="Collection location" error={fields.location}>
          <input
            value={values.location}
            onChange={(event) => change('location', event.target.value)}
            required
            maxLength={100}
          />
        </Field>
        <Field
          label="Equipment status"
          error={fields.status}
          hint="Active loans and approved reservations must be resolved before taking an asset out of service."
        >
          <select value={values.status} onChange={(event) => change('status', event.target.value)}>
            {['READY', 'MAINTENANCE', 'RETIRED'].map((status) => (
              <option key={status} value={status}>
                {statuses[status]}
              </option>
            ))}
          </select>
        </Field>
        <div className="dialog-actions">
          <button type="button" className="button secondary" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button className="button primary" disabled={busy}>
            {busy ? 'Saving...' : item ? 'Save equipment' : 'Add equipment'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function EquipmentBooking({
  detail,
  user,
  config,
  initialDates,
  onClose,
  onSaved,
  onDeleted,
  onEdit,
  notify,
}) {
  const item = detail.equipment;
  const [values, setValues] = useState({
    startDate: initialDates.start || config.today,
    endDate: initialDates.end || initialDates.start || config.today,
    purpose: '',
    requestKey: crypto.randomUUID(),
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const deletePending = useRef(false);
  const maxDate = addDays(config.today, config.advanceDays);
  function fillBooking() {
    if (
      detail.reservedDates.some(
        (slot) => slot.status === 'CHECKED_OUT' && slot.endDate < config.today,
      )
    ) {
      setError('This asset has an overdue loan. Choose another available asset.');
      return;
    }
    const duration = Math.min(2, config.maxDays, config.advanceDays + 1);
    let start = null;
    for (let offset = 0; offset <= config.advanceDays - duration + 1; offset++) {
      const candidate = addDays(config.today, offset);
      const last = addDays(candidate, duration - 1);
      if (
        !detail.reservedDates.some((slot) => slot.startDate <= last && slot.endDate >= candidate)
      ) {
        start = candidate;
        break;
      }
    }
    if (!start) {
      setError(
        'No available example dates were found. Choose another asset or reservation period.',
      );
      return;
    }
    setValues({
      startDate: start,
      endDate: addDays(start, duration - 1),
      purpose: 'Recording a campus equipment project presentation with our student team.',
      requestKey: crypto.randomUUID(),
    });
    setFields({});
    setError('');
  }
  const change = (key, value) =>
    setValues((previous) => ({
      ...previous,
      [key]: value,
      ...(key === 'startDate' && previous.endDate < value ? { endDate: value } : {}),
    }));
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setFields({});
    try {
      const result = await api('/api/reservations', {
        method: 'POST',
        body: { equipmentId: item.id, ...values },
      });
      notify(`${result.reference} submitted to the equipment desk.`);
      onSaved();
      onClose();
    } catch (failure) {
      setError(failure.message);
      setFields(failure.fields || {});
    } finally {
      setBusy(false);
    }
  }
  async function deleteEquipment() {
    if (deletePending.current) return;
    deletePending.current = true;
    setBusy(true);
    setError('');
    try {
      await api(`/api/equipment/${item.id}`, { method: 'DELETE' });
      notify('Equipment deleted.');
      onDeleted();
      onClose();
    } catch (failure) {
      setError(failure.message);
    } finally {
      deletePending.current = false;
      setBusy(false);
    }
  }
  if (confirmingDelete) {
    function cancelDelete() {
      if (deletePending.current) return;
      setError('');
      setConfirmingDelete(false);
    }
    return (
      <Dialog
        key="delete-equipment"
        title="Delete equipment"
        subtitle={`${item.name} / ${item.assetTag}`}
        onClose={cancelDelete}
        busy={busy}
      >
        <div className="dialog-body">
          <p>
            Are you sure you want to delete this equipment? All related records will also be
            permanently deleted.
          </p>
          <FormError error={error} />
          <div className="dialog-actions">
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={cancelDelete}
            >
              Cancel
            </button>
            <button
              type="button"
              className="button danger"
              disabled={busy}
              onClick={deleteEquipment}
            >
              {busy ? 'Deleting...' : 'Delete equipment'}
            </button>
          </div>
        </div>
      </Dialog>
    );
  }
  return (
    <Dialog
      title={item.name}
      subtitle={`${item.assetTag} / ${categories[item.category]}`}
      onClose={onClose}
      busy={busy}
      wide
    >
      <div className="equipment-detail">
        <div className="equipment-summary">
          <EquipmentArt category={item.category} />
          <div className="equipment-summary-body">
            <Badge status={item.availability} />
            <p>{item.description}</p>
            <div className="location-line">
              <Icon name="location" size={18} />
              {item.location}
            </div>
            <h3>Committed reservation dates</h3>
            {detail.reservedDates.length ? (
              <ul className="reserved-slots">
                {detail.reservedDates.map((slot, index) => (
                  <li key={index}>
                    <span>
                      {day(slot.startDate)} - {day(slot.endDate)}
                    </span>
                    <small>{statuses[slot.status]}</small>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted small-copy">No approved reservations or loans are recorded.</p>
            )}
            <p className="small-copy muted">
              Pending requests do not hold equipment. Dates are secured when the desk approves a
              request.
            </p>
          </div>
        </div>
        <div className="booking-form">
          {user.role === 'ADMIN' ? (
            <>
              <span className="eyebrow">DESK MANAGEMENT</span>
              <h3>Keep this asset ready.</h3>
              <p className="muted">Update its details, collection location or service status.</p>
              <div className="d-flex flex-wrap gap-2">
                <button className="button primary" onClick={() => onEdit(item)}>
                  <Icon name="edit" size={17} />
                  Edit equipment
                </button>
                <button className="button subtle-danger" onClick={() => setConfirmingDelete(true)}>
                  Delete equipment
                </button>
              </div>
            </>
          ) : item.status !== 'READY' ? (
            <Empty title="Temporarily unavailable" icon="info">
              This asset is not currently accepting reservations. Choose another item from the
              catalogue.
            </Empty>
          ) : (
            <form onSubmit={submit}>
              <span className="eyebrow">MAKE A RESERVATION</span>
              <h3>Plan your pickup.</h3>
              <p className="muted">
                Reserve for up to {config.maxDays} calendar days. Both the first and last day are
                included.
              </p>
              {config.demoEnabled && (
                <button type="button" className="button secondary demo-fill" onClick={fillBooking}>
                  Fill example dates and purpose
                </button>
              )}
              <FormError error={error} />
              <div className="form-grid">
                <Field label="Start date" error={fields.startDate}>
                  <DateInput
                    required
                    min={config.today}
                    max={maxDate}
                    value={values.startDate}
                    onChange={(event) => change('startDate', event.target.value)}
                  />
                </Field>
                <Field label="Return by" error={fields.endDate}>
                  <DateInput
                    required
                    min={values.startDate || config.today}
                    max={
                      values.startDate
                        ? [addDays(values.startDate, config.maxDays - 1), maxDate].sort()[0]
                        : maxDate
                    }
                    value={values.endDate}
                    onChange={(event) => change('endDate', event.target.value)}
                  />
                </Field>
              </div>
              <Field
                label="What will you use it for?"
                error={fields.purpose}
                hint={`${values.purpose.length}/500 characters. Add at least 10 characters.`}
              >
                <textarea
                  required
                  minLength={10}
                  maxLength={500}
                  rows={4}
                  placeholder="Tell the desk a little about your project."
                  value={values.purpose}
                  onChange={(event) => change('purpose', event.target.value)}
                />
              </Field>
              <div className="booking-note">
                <Icon name="info" size={17} />
                <span>
                  Bring your student card for collection. Your dates are confirmed only after
                  approval.
                </span>
              </div>
              <button className="button primary full-width" disabled={busy}>
                {busy ? 'Submitting...' : 'Send reservation request'}
                <Icon name="arrow" size={17} />
              </button>
            </form>
          )}
        </div>
      </div>
    </Dialog>
  );
}

export default function Catalog({ user, config, refresh, onChange, notify }) {
  const [filters, setFilters] = useState({
    search: '',
    category: '',
    availableOnly: false,
    start: '',
    end: '',
  });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState(null);
  const [editing, setEditing] = useState(undefined);
  const [opening, setOpening] = useState(null);
  const admin = user.role === 'ADMIN';
  const changeFilter = (key, value) => {
    setFilters((previous) => ({ ...previous, [key]: value }));
    setPage(0);
  };
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const invalidDate =
      dateMessage(filters.start, config.today, addDays(config.today, config.advanceDays)) ||
      dateMessage(
        filters.end,
        filters.start || config.today,
        addDays(config.today, config.advanceDays),
      );
    if (invalidDate) {
      setError(invalidDate);
      setLoading(false);
      return;
    }
    const timer = setTimeout(
      () =>
        api(`/api/equipment?${query({ ...filters, page, size: 9 })}`)
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
  }, [filters, page, refresh]);
  async function open(item) {
    setOpening(item.id);
    try {
      setDetail(await api(`/api/equipment/${item.id}`));
    } catch (failure) {
      notify(failure.message, 'error');
    } finally {
      setOpening(null);
    }
  }
  return (
    <>
      <section className="catalog-toolbar">
        <div className="search-field">
          <Icon name="search" size={19} />
          <input
            aria-label="Search equipment"
            placeholder="Search by equipment or asset tag"
            value={filters.search}
            onChange={(event) => changeFilter('search', event.target.value)}
          />
        </div>
        {config.demoEnabled && (
          <button
            type="button"
            className="button secondary"
            onClick={() => {
              setFilters({
                search: data?.items[0]?.assetTag || 'CAM-001',
                category: '',
                availableOnly: false,
                start: '',
                end: '',
              });
              setPage(0);
            }}
          >
            Fill example search
          </button>
        )}
        {admin && (
          <button className="button primary" onClick={() => setEditing(null)}>
            <Icon name="plus" size={18} />
            Add equipment
          </button>
        )}
      </section>
      <div className="category-tabs" role="group" aria-label="Equipment categories">
        {[['', 'All equipment'], ...Object.entries(categories)].map(([key, label]) => (
          <button
            key={key}
            className={filters.category === key ? 'selected' : ''}
            aria-pressed={filters.category === key}
            onClick={() => changeFilter('category', key)}
          >
            {label}
          </button>
        ))}
      </div>
      <section className="availability-filter">
        {config.demoEnabled && (
          <button
            className="button small secondary"
            onClick={() => {
              setFilters({
                search: '',
                category: '',
                availableOnly: true,
                start: config.today,
                end: addDays(config.today, 1),
              });
              setPage(0);
            }}
          >
            Use demo dates
          </button>
        )}
        <div className="availability-label">
          <Icon name="filter" size={18} />
          <strong>Plan your dates</strong>
        </div>
        <label>
          From
          <DateInput
            value={filters.start}
            min={config.today}
            max={addDays(config.today, config.advanceDays)}
            onChange={(event) => {
              const value = event.target.value;
              setFilters((previous) => ({
                ...previous,
                start: value,
                end: previous.end < value ? value : previous.end,
              }));
              setPage(0);
            }}
          />
        </label>
        <label>
          To
          <DateInput
            value={filters.end}
            min={filters.start || config.today}
            max={addDays(config.today, config.advanceDays)}
            onChange={(event) => changeFilter('end', event.target.value)}
          />
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filters.availableOnly}
            onChange={(event) => changeFilter('availableOnly', event.target.checked)}
          />
          Available only
        </label>
        {(filters.start ||
          filters.end ||
          filters.availableOnly ||
          filters.search ||
          filters.category) && (
          <button
            className="text-button"
            onClick={() => {
              setFilters({ search: '', category: '', availableOnly: false, start: '', end: '' });
              setPage(0);
            }}
          >
            Clear filters
          </button>
        )}
      </section>
      <div className="catalog-results">
        <span>
          {data
            ? `${data.total} ${data.total === 1 ? 'asset' : 'assets'} found`
            : 'Equipment catalogue'}
        </span>
        <span>
          Availability for {day(filters.start || config.today)}
          {filters.end && filters.end !== filters.start ? ` - ${day(filters.end)}` : ''}
        </span>
      </div>
      <FormError error={error} />
      {loading ? (
        <Loading label="Finding equipment..." />
      ) : !data?.items.length ? (
        <Empty title="No equipment matches your search" icon="search">
          Try another category, remove a filter or choose different dates.
        </Empty>
      ) : (
        <div className="equipment-grid">
          {data.items.map((item) => (
            <article className="equipment-card" key={item.id}>
              <button
                className="equipment-art-button"
                aria-label={`View ${item.name}`}
                onClick={() => open(item)}
                disabled={opening === item.id}
              >
                <EquipmentArt category={item.category} />
                <span className="asset-tag">{item.assetTag}</span>
              </button>
              <div className="equipment-card-body">
                <div className="equipment-card-category">{categories[item.category]}</div>
                <h2>
                  <button onClick={() => open(item)} disabled={opening === item.id}>
                    {item.name}
                  </button>
                </h2>
                <p>{item.description}</p>
                <div className="equipment-card-location">
                  <Icon name="location" size={15} />
                  {item.location}
                </div>
                <div className="equipment-card-footer">
                  <Badge status={item.availability} />
                  <button
                    className="text-button"
                    onClick={() => open(item)}
                    disabled={opening === item.id}
                  >
                    {opening === item.id ? 'Opening...' : admin ? 'Manage' : 'Details'}
                    <Icon name="arrow" size={16} />
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      <Pagination data={loading ? null : data} onPage={setPage} />
      {detail && (
        <EquipmentBooking
          detail={detail}
          user={user}
          config={config}
          initialDates={filters}
          onClose={() => setDetail(null)}
          onSaved={onChange}
          onDeleted={() => {
            setPage(0);
            onChange();
          }}
          notify={notify}
          onEdit={(item) => {
            setDetail(null);
            setEditing(item);
          }}
        />
      )}
      {editing !== undefined && (
        <EquipmentEditor
          config={config}
          item={editing}
          onClose={() => setEditing(undefined)}
          onSaved={onChange}
          notify={notify}
        />
      )}
    </>
  );
}

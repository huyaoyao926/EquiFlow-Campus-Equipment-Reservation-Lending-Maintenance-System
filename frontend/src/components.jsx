import { useEffect, useId, useRef } from 'react';
import { categories, statuses } from './api';

const paths = {
  home: 'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  box: 'M3 7 12 3l9 4v10l-9 4-9-4ZM3 7l9 4 9-4M12 11v10M7.5 5l9 4',
  calendar:
    'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2ZM8 14h2M14 14h2M8 18h2',
  clock: 'M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  chevron: 'm9 5 7 7-7 7',
  search: 'm21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  logout: 'M9 4H4v16h5M13 7l5 5-5 5M8 12h13',
  close: 'm6 6 12 12M6 18 18 6',
  check: 'm5 12 4 4L19 6',
  plus: 'M12 5v14M5 12h14',
  filter: 'M3 5h18M6 12h12M10 19h4',
  refresh: 'M20 7v5h-5M4 17v-5h5M5 8a8 8 0 0 1 13-3l2 3M4 16l2 3a8 8 0 0 0 13-3',
  edit: 'm14 5 5 5M4 20l4-1L20 7a2.8 2.8 0 0 0-4-4L4 15ZM4 20h16',
  download: 'M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  location: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  user: 'M20 21v-2a6 6 0 0 0-6-6h-4a6 6 0 0 0-6 6v2M16 5a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  shield: 'm12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6ZM8 12l3 3 5-6',
  info: 'M12 11v6M12 7v.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  camera: 'M3 7h4l2-3h6l2 3h4v14H3ZM16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  audio: 'M9 4a3 3 0 0 1 6 0v8a3 3 0 0 1-6 0ZM5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8',
  computing: 'M3 3h18v13H3ZM1 21h22M8 21l1-5M16 21l-1-5',
  lab: 'M9 2h6M10 2v7L4 19a2 2 0 0 0 2 3h12a2 2 0 0 0 2-3L14 9V2M8 14h8',
  accessories: 'M8 3h8v6H8ZM12 9v6M4 22l8-7 8 7M12 15v7',
  mail: 'M3 5h18v14H3ZM3 5l9 8 9-8',
  key: 'm15 7 6 6-3 3-2-2-2 2-2-2M14 7a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z',
};

export function Icon({ name, size = 20, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name] || paths.box} />
    </svg>
  );
}

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">
        <Icon name="box" size={23} />
      </span>
      <span>EquiFlow</span>
    </div>
  );
}

export function Badge({ status, overdue }) {
  return (
    <span className={`badge badge-${overdue ? 'overdue' : status.toLowerCase()}`}>
      <i />
      {overdue ? 'Overdue' : statuses[status] || status}
    </span>
  );
}

export function EquipmentArt({ category, compact = false }) {
  const symbol = {
    CAMERA: 'camera',
    AUDIO: 'audio',
    COMPUTING: 'computing',
    LAB: 'lab',
    ACCESSORIES: 'accessories',
  }[category];
  return (
    <div className={`equipment-art art-${category.toLowerCase()} ${compact ? 'compact' : ''}`}>
      <div className="art-ring" />
      <Icon name={symbol} size={compact ? 32 : 72} />
      <span>{categories[category]}</span>
    </div>
  );
}

export function Field({ label, error, children, hint, className = '' }) {
  return (
    <label className={`field ${className} ${error ? 'field-invalid' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? (
        <span className="field-error">{error}</span>
      ) : (
        hint && <span className="field-hint">{hint}</span>
      )}
    </label>
  );
}

export function FormError({ error }) {
  return error ? (
    <div className="form-error" role="alert">
      <Icon name="info" size={18} />
      <span>{error}</span>
    </div>
  ) : null;
}

export function Empty({ title, children, icon = 'box', action }) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon name={icon} size={28} />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  );
}

export function Loading({ label = 'Loading records...' }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      {label}
    </div>
  );
}

export function Pagination({ data, onPage }) {
  if (!data || data.total === 0) return null;
  return (
    <div className="pagination">
      <span>
        Showing {data.page * data.size + 1}-{Math.min((data.page + 1) * data.size, data.total)} of{' '}
        {data.total}
      </span>
      <div>
        <button
          className="button small secondary"
          disabled={data.page === 0}
          onClick={() => onPage(data.page - 1)}
        >
          Previous
        </button>
        <span className="page-number">
          {data.page + 1} / {Math.max(data.pages, 1)}
        </span>
        <button
          className="button small secondary"
          disabled={data.page + 1 >= data.pages}
          onClick={() => onPage(data.page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}

export function Dialog({ title, subtitle, children, onClose, busy = false, wide = false }) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  const busyRef = useRef(busy);
  const id = useId();
  closeRef.current = onClose;
  busyRef.current = busy;
  useEffect(() => {
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusables = () => [
      ...ref.current.querySelectorAll(
        'button:not(:disabled), input:not(:disabled), textarea, select, a[href], [tabindex="0"]',
      ),
    ];
    (focusables()[0] || ref.current).focus();
    function keydown(event) {
      if (event.key === 'Escape' && !busyRef.current) closeRef.current();
      if (event.key === 'Tab') {
        const items = focusables();
        if (!items.length) return event.preventDefault();
        if (event.shiftKey && document.activeElement === items[0]) {
          event.preventDefault();
          items.at(-1).focus();
        } else if (!event.shiftKey && document.activeElement === items.at(-1)) {
          event.preventDefault();
          items[0].focus();
        }
      }
    }
    document.addEventListener('keydown', keydown);
    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', keydown);
      previous?.focus();
    };
  }, []);
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <section
        ref={ref}
        className={`dialog ${wide ? 'dialog-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        tabIndex={-1}
      >
        <header className="dialog-header">
          <div>
            <h2 id={id}>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button
            aria-label="Close dialog"
            className="icon-button"
            disabled={busy}
            onClick={onClose}
          >
            <Icon name="close" />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

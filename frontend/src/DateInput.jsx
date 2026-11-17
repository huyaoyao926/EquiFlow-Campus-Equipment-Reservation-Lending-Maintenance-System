import { useEffect, useRef } from 'react';
import { dateMessage } from './englishValidation';

export default function DateInput({
  value,
  min,
  max,
  required = false,
  onChange,
  className = '',
  ...props
}) {
  const input = useRef(null);
  useEffect(() => {
    input.current?.setCustomValidity(dateMessage(value, min, max, required));
  }, [value, min, max, required]);
  return (
    <input
      {...props}
      ref={input}
      className={`iso-date-input ${className}`.trim()}
      type="text"
      lang="en"
      inputMode="text"
      autoComplete="off"
      placeholder="YYYY-MM-DD"
      title="Use YYYY-MM-DD."
      pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
      maxLength={10}
      data-english-date="true"
      required={required}
      min={min}
      max={max}
      value={value}
      onChange={(event) => {
        event.target.setCustomValidity(dateMessage(event.target.value, min, max, required));
        onChange(event);
      }}
    />
  );
}

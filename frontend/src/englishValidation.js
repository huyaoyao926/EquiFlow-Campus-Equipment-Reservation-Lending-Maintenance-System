export function dateMessage(value, min, max, required = false) {
  if (!value) return required ? 'Choose a date in YYYY-MM-DD format.' : '';
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || Number(value.slice(0, 4)) < 1)
    return 'Enter a valid date in YYYY-MM-DD format.';
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value)
    return 'Enter a valid date in YYYY-MM-DD format.';
  if (min && value < min) return `Choose ${min} or a later date.`;
  if (max && value > max) return `Choose ${max} or an earlier date.`;
  return '';
}

function validationMessage(input) {
  if (input.dataset.englishDate === 'true')
    return dateMessage(input.value, input.min, input.max, input.required);
  const validity = input.validity;
  if (validity.valueMissing) return 'Complete this field.';
  if (validity.typeMismatch)
    return input.type === 'email' ? 'Enter a valid email address.' : 'Enter a valid value.';
  if (validity.tooShort) return `Enter at least ${input.minLength} characters.`;
  if (validity.tooLong) return `Enter no more than ${input.maxLength} characters.`;
  if (validity.patternMismatch) return input.title || 'Use the requested format.';
  if (validity.rangeUnderflow) return `Enter ${input.min} or a larger value.`;
  if (validity.rangeOverflow) return `Enter ${input.max} or a smaller value.`;
  if (validity.stepMismatch || validity.badInput) return 'Enter a valid value.';
  return validity.customError ? input.validationMessage : '';
}

export function installEnglishValidation() {
  document.documentElement.lang = 'en';
  document.addEventListener(
    'invalid',
    (event) => {
      if (typeof event.target.setCustomValidity === 'function')
        event.target.setCustomValidity(validationMessage(event.target) || 'Check this field.');
    },
    true,
  );
  document.addEventListener(
    'input',
    (event) => {
      if (typeof event.target.setCustomValidity === 'function') event.target.setCustomValidity('');
    },
    true,
  );
  document.addEventListener(
    'click',
    (event) => {
      const button = event.target.closest?.('button');
      if (!button || !/^Fill |^Use demo /.test(button.textContent.trim())) return;
      button
        .closest('form')
        ?.querySelectorAll('input, textarea, select')
        .forEach((input) => {
          input.setCustomValidity('');
        });
    },
    true,
  );
}

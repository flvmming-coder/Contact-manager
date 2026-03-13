# Contact Manager v0.11.6b

Changes:
- Contact form now includes: first name, last name (optional), phone, email (optional), address (optional), birthday (optional).
- Email validation: if email is entered, it must contain `@`.
- Birthday supports manual input (`dd/mm/yyyy`) and calendar picker.
- Birthday uses strict date validation and typing normalization (`99` -> `9/9`).
- UI update:
  - round add button (FAB) in bottom-right corner with plus icon,
  - top header with contact icon on the left,
  - white `Contact Manager` title,
  - counters (total/imported) inside the same header,
  - left-to-right gradient `#1e293b` -> `#0f172a`.

Assets:
- Contact header icon was adapted from `svgs-full/solid/address-book.svg`.

APK:
- `ContactManager-v0.11.6b-debug.apk`

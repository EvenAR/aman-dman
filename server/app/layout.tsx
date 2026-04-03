import type { Metadata } from 'next';

import 'leaflet/dist/leaflet.css';
import 'leaflet-draw/dist/leaflet.draw.css';
import '../web/src/styles.css';

export const metadata: Metadata = {
  title: 'AMAN/DMAN Config Editor',
  description: 'Hosted AMAN/DMAN operational configuration editor.',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>): React.JSX.Element {
  return (
    <html lang="en">
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}

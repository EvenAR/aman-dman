'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

interface NavItem {
  href: string;
  label: string;
}

export function PathnameNav({
  items,
  className = 'section-nav',
}: {
  items: NavItem[];
  className?: string;
}): React.JSX.Element {
  const pathname = usePathname();

  return (
    <nav className={className}>
      {items.map((item) => {
        const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={active ? 'section-nav__item section-nav__item--active' : 'section-nav__item'}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

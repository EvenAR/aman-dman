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
  const activeHref = items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((left, right) => right.href.length - left.href.length)[0]?.href;

  return (
    <nav className={className}>
      {items.map((item) => {
        const active = item.href === activeHref;
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

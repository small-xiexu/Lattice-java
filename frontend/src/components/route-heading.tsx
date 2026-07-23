import { useEffect, useRef, type PropsWithChildren } from "react";

export function RouteHeading({ children }: PropsWithChildren) {
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <h1 id="route-heading" ref={headingRef} tabIndex={-1}>
      {children}
    </h1>
  );
}

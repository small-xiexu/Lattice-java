import { SKIP, visit } from "unist-util-visit";
import type { Node } from "unist";

import type { ExactCitationBinding } from "./citation-types";

interface MutableTextNode {
  type: "text";
  value: string;
  position?: {
    start: { offset?: number };
  };
  data?: {
    hName?: string;
    hProperties?: Record<string, unknown>;
  };
}

interface MutableParentNode {
  children: unknown[];
}

export function remarkCitationMarkers(bindings: ExactCitationBinding[]) {
  const sortedBindings = [...bindings].sort(
    (left, right) => left.startOffset - right.startOffset,
  );
  return () => (tree: Node) => {
    visit(tree, "text", (node, index, parent) => {
      const textNode = node as MutableTextNode;
      const parentNode = parent as MutableParentNode | undefined;
      const nodeStart = textNode.position?.start.offset;
      if (index === undefined || !parentNode || nodeStart === undefined) {
        return;
      }
      const nodeEnd = nodeStart + textNode.value.length;
      const nodeBindings = sortedBindings.filter(
        (binding) =>
          binding.startOffset >= nodeStart && binding.endOffset <= nodeEnd,
      );
      if (nodeBindings.length === 0) {
        return;
      }
      const replacements: MutableTextNode[] = [];
      let localOffset = 0;
      nodeBindings.forEach((binding) => {
        const markerStart = binding.startOffset - nodeStart;
        const markerEnd = binding.endOffset - nodeStart;
        if (markerStart > localOffset) {
          replacements.push({
            type: "text",
            value: textNode.value.slice(localOffset, markerStart),
          });
        }
        replacements.push({
          type: "text",
          value: textNode.value.slice(markerStart, markerEnd),
          data: {
            hName: "button",
            hProperties: {
              type: "button",
              className: ["citation-marker-host"],
              dataMarkerId: binding.marker.markerId,
            },
          },
        });
        localOffset = markerEnd;
      });
      if (localOffset < textNode.value.length) {
        replacements.push({
          type: "text",
          value: textNode.value.slice(localOffset),
        });
      }
      parentNode.children.splice(index, 1, ...replacements);
      return [SKIP, index + replacements.length] as const;
    });
  };
}

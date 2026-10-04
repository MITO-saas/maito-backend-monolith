import React from 'react';
import { PromoStrip } from './PromoStrip';
import { HeroCarousel } from './HeroCarousel';
import { FeaturedGrid } from './FeaturedGrid';
import { BrandStory } from './BrandStory';
import { FooterLinks } from './FooterLinks';

export interface SectionWidget {
  sectionId: string;
  componentType: string;
  displayOrder: number;
  contentPayload: Record<string, any>;
}

export interface SDUIRendererProps {
  sections: SectionWidget[];
  onAddToCart?: (name: string) => void;
}

const COMPONENT_MAP: Record<string, React.FC<any>> = {
  PROMO_STRIP: PromoStrip,
  HERO_CAROUSEL: HeroCarousel,
  FEATURED_GRID: FeaturedGrid,
  BRAND_STORY: BrandStory,
  FOOTER_LINKS: FooterLinks,
};

export const SDUIRenderer: React.FC<SDUIRendererProps> = ({ sections, onAddToCart }) => {
  const sorted = [...sections].sort((a, b) => a.displayOrder - b.displayOrder);

  return (
    <div className="sdui-component-tree">
      {sorted.map(section => {
        const Component = COMPONENT_MAP[section.componentType];
        if (!Component) {
          console.warn('SDUI: Unknown component type:', section.componentType);
          return null;
        }
        return (
          <Component
            key={section.sectionId}
            sectionId={section.sectionId}
            payload={section.contentPayload}
            onAddToCart={onAddToCart}
          />
        );
      })}
    </div>
  );
};

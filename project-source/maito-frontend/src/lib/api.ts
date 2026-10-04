export interface PageLayoutResponse {
  pageSlug: string;
  title: string;
  seo: Record<string, any>;
  theme: {
    themeName: string;
    tokens: {
      primaryColor?: string;
      fontHeadings?: string;
      fontBody?: string;
      borderRadiusPx?: number;
      [key: string]: any;
    };
  };
  sections: Array<{
    sectionId: string;
    componentType: string;
    displayOrder: number;
    contentPayload: Record<string, any>;
  }>;
}

export async function fetchPublishedLayout(
  slug = 'home',
  tenantId = 'mito_crunch'
): Promise<PageLayoutResponse> {
  const res = await fetch(`/api/v1/cms/pages/${slug}`, {
    headers: {
      'X-Tenant-ID': tenantId,
      'Accept': 'application/json'
    }
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch SDUI layout: HTTP ${res.status}`);
  }

  const json = await res.json();
  if (!json.success || !json.data) {
    throw new Error(json.error?.message || 'Invalid SDUI response');
  }

  return json.data;
}

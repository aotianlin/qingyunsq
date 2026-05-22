import type { ResourceVO } from '@/types/resource';

export const ALL_RESOURCE_TOPIC = '__all__';
export const UNTAGGED_RESOURCE_TOPIC = '未分类';

export interface ResourceTopicOption {
  label: string;
  count: number;
}

export interface ResourceTopicGroup {
  key: string;
  label: string;
  count: number;
  items: ResourceVO[];
}

type TopicSource = Pick<ResourceVO, 'tags' | 'course' | 'major' | 'college'>;

function normalizeTopic(value: string | null | undefined): string {
  return (value || '').trim();
}

function dedupeTopics(values: Array<string | null | undefined>): string[] {
  const seen = new Set<string>();
  const topics: string[] = [];

  for (const value of values) {
    const topic = normalizeTopic(value);
    if (!topic || seen.has(topic)) continue;
    seen.add(topic);
    topics.push(topic);
  }

  return topics;
}

export function getResourceTopics(resource: TopicSource): string[] {
  const topics = dedupeTopics([
    ...(resource.tags || []),
    resource.course,
    resource.major,
    resource.college,
  ]);

  return topics.length > 0 ? topics : [UNTAGGED_RESOURCE_TOPIC];
}

export function getPrimaryResourceTopic(resource: TopicSource): string {
  return getResourceTopics(resource)[0] || UNTAGGED_RESOURCE_TOPIC;
}

export function buildResourceTopicOptions(resources: ResourceVO[]): ResourceTopicOption[] {
  const topicCounts = new Map<string, number>();

  for (const resource of resources) {
    const resourceTopics = new Set(getResourceTopics(resource));
    for (const topic of resourceTopics) {
      topicCounts.set(topic, (topicCounts.get(topic) || 0) + 1);
    }
  }

  return [...topicCounts.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'zh-Hans-CN'));
}

export function buildResourceTopicGroups(
  resources: ResourceVO[],
  activeTopic = ALL_RESOURCE_TOPIC,
): ResourceTopicGroup[] {
  const groupMap = new Map<string, ResourceVO[]>();

  for (const resource of resources) {
    const topics = getResourceTopics(resource);
    if (activeTopic !== ALL_RESOURCE_TOPIC && !topics.includes(activeTopic)) {
      continue;
    }

    const groupLabel = activeTopic === ALL_RESOURCE_TOPIC ? getPrimaryResourceTopic(resource) : activeTopic;
    const items = groupMap.get(groupLabel) || [];
    items.push(resource);
    groupMap.set(groupLabel, items);
  }

  return [...groupMap.entries()]
    .map(([label, items]) => ({
      key: label,
      label,
      count: items.length,
      items,
    }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'zh-Hans-CN'));
}

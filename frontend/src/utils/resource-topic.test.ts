import { describe, expect, it } from 'vitest';
import {
  ALL_RESOURCE_TOPIC,
  buildResourceTopicGroups,
  buildResourceTopicOptions,
  getPrimaryResourceTopic,
  getResourceTopics,
  UNTAGGED_RESOURCE_TOPIC,
} from './resource-topic';
import type { ResourceVO } from '@/types/resource';

function makeResource(overrides: Partial<ResourceVO> & Pick<ResourceVO, 'id' | 'fileName' | 'fileSize' | 'fileType' | 'visibility' | 'downloadCount' | 'collectCount' | 'createdAt'>): ResourceVO {
  return {
    uploaderId: 1,
    uploader: null,
    spaceId: null,
    college: null,
    major: null,
    course: null,
    semester: null,
    tags: [],
    version: null,
    description: null,
    ...overrides,
  } as ResourceVO;
}

describe('resource-topic helpers', () => {
  it('uses tags first and falls back to structured topics', () => {
    const resource = makeResource({
      id: 1,
      fileName: 'a.pdf',
      fileSize: 100,
      fileType: 'pdf',
      visibility: 'PUBLIC',
      downloadCount: 0,
      collectCount: 0,
      createdAt: '2026-05-21T00:00:00',
      tags: ['Java', '  期末复习  ', 'Java'],
      course: '数据库',
      major: '软件工程',
      college: '计算机学院',
    });

    expect(getResourceTopics(resource)).toEqual(['Java', '期末复习', '数据库', '软件工程', '计算机学院']);
    expect(getPrimaryResourceTopic(resource)).toBe('Java');
  });

  it('marks resources without any topic data as untagged', () => {
    const resource = makeResource({
      id: 2,
      fileName: 'b.pdf',
      fileSize: 100,
      fileType: 'pdf',
      visibility: 'PUBLIC',
      downloadCount: 0,
      collectCount: 0,
      createdAt: '2026-05-21T00:00:00',
    });

    expect(getResourceTopics(resource)).toEqual([UNTAGGED_RESOURCE_TOPIC]);
    expect(getPrimaryResourceTopic(resource)).toBe(UNTAGGED_RESOURCE_TOPIC);
  });

  it('builds topic options and groups resources by topic', () => {
    const resources = [
      makeResource({
        id: 1,
        fileName: 'a.pdf',
        fileSize: 100,
        fileType: 'pdf',
        visibility: 'PUBLIC',
        downloadCount: 0,
        collectCount: 0,
        createdAt: '2026-05-21T00:00:00',
        tags: ['Java'],
      }),
      makeResource({
        id: 2,
        fileName: 'b.pdf',
        fileSize: 100,
        fileType: 'pdf',
        visibility: 'PUBLIC',
        downloadCount: 0,
        collectCount: 0,
        createdAt: '2026-05-21T00:00:00',
        tags: ['Java', 'Spring'],
      }),
      makeResource({
        id: 3,
        fileName: 'c.pdf',
        fileSize: 100,
        fileType: 'pdf',
        visibility: 'PUBLIC',
        downloadCount: 0,
        collectCount: 0,
        createdAt: '2026-05-21T00:00:00',
        course: '线性代数',
      }),
    ];

    const options = buildResourceTopicOptions(resources);
    expect(options[0]).toEqual({ label: 'Java', count: 2 });
    expect(options.some((item) => item.label === '线性代数')).toBe(true);

    const groups = buildResourceTopicGroups(resources, ALL_RESOURCE_TOPIC);
    expect(groups).toHaveLength(2);
    expect(groups[0].label).toBe('Java');
    expect(groups[0].count).toBe(2);
    expect(groups[1].label).toBe('线性代数');

    const springGroups = buildResourceTopicGroups(resources, 'Spring');
    expect(springGroups).toHaveLength(1);
    expect(springGroups[0].label).toBe('Spring');
    expect(springGroups[0].items.map((item) => item.id)).toEqual([2]);
  });
});

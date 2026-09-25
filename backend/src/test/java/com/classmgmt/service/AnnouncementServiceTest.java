package com.classmgmt.service;

import com.classmgmt.config.AppProperties;
import com.classmgmt.domain.Announcement;
import com.classmgmt.domain.AnnouncementVersion;
import com.classmgmt.repo.AnnouncementFileRepository;
import com.classmgmt.repo.AnnouncementRepository;
import com.classmgmt.repo.AnnouncementVersionRepository;
import com.classmgmt.web.GlobalExceptionHandler.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private AnnouncementVersionRepository versionRepository;
    @Mock
    private AnnouncementFileRepository fileRepository;

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcementRepository, versionRepository, fileRepository, new AppProperties());
    }

    private Announcement announcement(long id, String editedText) {
        Announcement a = new Announcement();
        a.setId(id);
        a.setTitle("测试通知");
        a.setEditedText(editedText);
        return a;
    }

    @Test
    void changedEditedTextIsArchivedAsVersion() {
        Announcement a = announcement(1L, "旧稿");
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, Map.of("editedText", "新稿"));

        ArgumentCaptor<AnnouncementVersion> captor = ArgumentCaptor.forClass(AnnouncementVersion.class);
        verify(versionRepository).save(captor.capture());
        assertEquals("旧稿", captor.getValue().getContent());
        assertEquals(1L, captor.getValue().getAnnouncementId());
        assertEquals("新稿", a.getEditedText());
    }

    @Test
    void unchangedEditedTextIsNotArchived() {
        Announcement a = announcement(1L, "同样的话");
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, Map.of("editedText", "同样的话"));

        verify(versionRepository, never()).save(any());
    }

    @Test
    void clearingEditedTextArchivesOldVersion() {
        Announcement a = announcement(1L, "旧稿");
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, Map.of("editedText", ""));

        verify(versionRepository).save(any(AnnouncementVersion.class));
        assertNull(a.getEditedText());
    }

    @Test
    void createRequiresTitle() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(Map.of("editedText", "内容")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void markSentRecordsTimeOnlyOnce() {
        Announcement a = announcement(1L, "内容");
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markSent(1L);
        assertNotNull(a.getSentAt());
        var first = a.getSentAt();

        service.markSent(1L);
        assertEquals(first, a.getSentAt());
        assertEquals(Announcement.STATUS_SENT, a.getStatus());
    }

    @Test
    void restoreVersionArchivesCurrentAndSwapsContent() {
        Announcement a = announcement(1L, "当前稿");
        when(announcementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AnnouncementVersion v = new AnnouncementVersion();
        v.setId(9L);
        v.setAnnouncementId(1L);
        v.setContent("历史稿");
        when(versionRepository.findById(9L)).thenReturn(Optional.of(v));

        service.restoreVersion(1L, 9L);

        ArgumentCaptor<AnnouncementVersion> captor = ArgumentCaptor.forClass(AnnouncementVersion.class);
        verify(versionRepository).save(captor.capture());
        assertEquals("当前稿", captor.getValue().getContent());
        assertEquals("历史稿", a.getEditedText());
    }
}
